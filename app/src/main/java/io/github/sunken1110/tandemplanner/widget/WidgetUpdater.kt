package io.github.sunken1110.tandemplanner.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.glance.GlanceId
import androidx.glance.appwidget.compose
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import io.github.sunken1110.tandemplanner.currentDate
import io.github.sunken1110.tandemplanner.data.SharedCalendar
import io.github.sunken1110.tandemplanner.ui.common.colorOf
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps the home-screen widget up to date:
 * - [publish]: the app saves its events for the widget whenever they change.
 * - Hourly: a background job reads the server, so events the other member added
 *   show up even if this app isn't opened ([schedulePeriodic]).
 * - On time: the widget is redrawn at midnight (D-2 → D-1, a trip starting) and
 *   when one of today's timed events passes ([scheduleTick]).
 */
object WidgetUpdater {
    private const val WORK_SYNC = "widget-sync"
    private const val WORK_TICK = "widget-tick"

    /** Saves the calendar's events for the widget and redraws it. */
    suspend fun publish(context: Context, calendar: SharedCalendar) {
        val items = calendar.events.toList().map { e ->
            WidgetItem(
                event = e,
                author = calendar.displayName(e.authorUid, e.authorName),
                color = calendar.colorOf(e.authorUid)
            )
        }
        WidgetStore.save(context, linked = calendar.roomCode != null, items = items, today = currentDate())
        render(context)
    }

    /** Reads the server once and publishes the result (the hourly background job). */
    suspend fun syncFromServer(context: Context) {
        val calendar = SharedCalendar(context)
        if (calendar.roomCode == null) {
            WidgetStore.save(context, linked = false, items = emptyList(), today = currentDate())
            render(context)
        } else if (calendar.refreshOnce()) {
            publish(context, calendar)
        } else {
            // Offline or failed: keep the saved events, but still redraw for the date.
            render(context)
        }
    }

    /**
     * One redraw at a time: the app's publish and the hourly job can both redraw at
     * once, and whichever reached Android last would win, possibly with older events.
     */
    private val drawing = Mutex()

    /** Redraws every placed widget (if any) and plans the next timed redraw. */
    suspend fun render(context: Context) {
        val ids = placedIds(context)
        if (ids.isEmpty()) return
        drawing.withLock { ids.forEach { runCatching { draw(context, it) } } }
        scheduleTick(context)
        // Also makes sure the hourly refresh exists (cheap: an existing one is kept).
        // It is set up when the first widget is placed, but clearing the app's storage
        // removes it while the widgets stay, and nothing else would bring it back.
        schedulePeriodic(context)
    }

    /**
     * Redraws one widget, right away.
     *
     * Glance normally draws widgets in a background job (a "session"), and Android
     * may hold that job back while the app is in the background, so changes showed
     * up seconds late. Instead, the widget is drawn here directly ([compose]) and
     * handed to Android's widget manager. Only if that fails does it fall back to
     * Glance's usual (slower) update.
     *
     * A new refresh stamp is saved in the widget's state first and drawn with it;
     * the widget reports it back once drawn ([WidgetStyleStore.renderedStamp]),
     * which is how the settings screen knows its change is on the home screen.
     * Returns that stamp.
     */
    suspend fun refresh(context: Context, id: GlanceId): Long = drawing.withLock { draw(context, id) }

    /** [refresh] without taking the lock (for callers that already hold it). */
    private suspend fun draw(context: Context, id: GlanceId): Long {
        val stamp = System.currentTimeMillis()
        updateAppWidgetState(context, id) { it[RefreshStamp] = stamp }
        val direct = runCatching {
            val glanceManager = GlanceAppWidgetManager(context)
            val appWidgetId = glanceManager.getAppWidgetId(id)
            val manager = AppWidgetManager.getInstance(context)
            val state = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
            val views = TandemWidget().compose(
                context = context,
                id = id,
                options = manager.getAppWidgetOptions(appWidgetId),
                state = state
            )
            manager.updateAppWidget(appWidgetId, views)
        }
        if (direct.isFailure) TandemWidget().update(context, id)
        return stamp
    }

    /** All placed widgets, of every kind. */
    private suspend fun placedIds(context: Context): List<GlanceId> = runCatching {
        val manager = GlanceAppWidgetManager(context)
        AllWidgetKinds.flatMap { manager.getGlanceIds(it) }
    }.getOrDefault(emptyList())

    /** True while at least one widget of any kind is on a home screen. */
    fun anyWidgetPlaced(context: Context): Boolean = runCatching {
        val manager = AppWidgetManager.getInstance(context)
        listOf(TandemWidgetReceiver::class.java, TandemTileWidgetReceiver::class.java, TandemListWidgetReceiver::class.java)
            .any { manager.getAppWidgetIds(ComponentName(context, it)).isNotEmpty() }
    }.getOrDefault(false)

    /** Starts the hourly server refresh (only runs with a network connection). */
    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(1, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(workDataOf(WidgetRefreshWorker.KEY_SYNC to true))
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_SYNC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Stops all widget jobs (the last widget was removed). */
    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).run {
            cancelUniqueWork(WORK_SYNC)
            cancelUniqueWork(WORK_TICK)
        }
    }

    /**
     * Plans a redraw (no network) at the next moment the widget's content changes
     * on its own: the end of a timed event today, or else midnight.
     */
    private fun scheduleTick(context: Context) {
        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        val midnight = today.plusDays(1).atStartOfDay()
        // Times are stored as "HH:mm"; an event "passes" one minute after its time.
        val nextTimed = WidgetStore.load(context).items.asSequence()
            .map { it.event }
            .filter { it.date == today && !it.isMultiDay }
            .mapNotNull { e -> runCatching { LocalTime.parse(e.time) }.getOrNull() }
            .map { today.atTime(it).plusMinutes(1) }
            .filter { it.isAfter(now) }
            .minOrNull()
        val at = nextTimed ?: midnight.plusMinutes(1)
        val delay = Duration.between(now, at).toMillis().coerceAtLeast(60_000L)
        val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(WidgetRefreshWorker.KEY_SYNC to false))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_TICK, ExistingWorkPolicy.REPLACE, request)
    }
}
