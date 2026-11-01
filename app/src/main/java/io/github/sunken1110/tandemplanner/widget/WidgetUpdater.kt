package io.github.sunken1110.tandemplanner.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
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

    /** Redraws every placed widget (if any) and plans the next timed redraw. */
    suspend fun render(context: Context) {
        if (!hasWidgets(context)) return
        TandemWidget().updateAll(context)
        scheduleTick(context)
    }

    private suspend fun hasWidgets(context: Context): Boolean =
        runCatching { GlanceAppWidgetManager(context).getGlanceIds(TandemWidget::class.java).isNotEmpty() }
            .getOrDefault(false)

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
        val nextTimed = WidgetStore.load(context).items
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
