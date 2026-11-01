package io.github.sunken1110.tandemplanner.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import io.github.sunken1110.tandemplanner.MainActivity
import io.github.sunken1110.tandemplanner.data.upcoming
import io.github.sunken1110.tandemplanner.model.Season
import io.github.sunken1110.tandemplanner.model.seasonEnd
import io.github.sunken1110.tandemplanner.ui.common.ddayBadgeColors
import io.github.sunken1110.tandemplanner.ui.common.ddayStepOf
import io.github.sunken1110.tandemplanner.ui.common.ddayText
import io.github.sunken1110.tandemplanner.ui.common.displayTime
import io.github.sunken1110.tandemplanner.ui.common.eventWhenText
import io.github.sunken1110.tandemplanner.ui.common.periodLabel
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** Intent extras the widget opens the app with (read in MainActivity). */
object WidgetExtras {
    /** ISO date ("2026-11-03") of the day to open in the month view. */
    const val DATE = "widget_date"

    /** True to open the quick-add bar right away. */
    const val QUICK_ADD = "widget_quick_add"
}

private val DateKey = ActionParameters.Key<String>(WidgetExtras.DATE)
private val QuickAddKey = ActionParameters.Key<Boolean>(WidgetExtras.QUICK_ADD)

/** Dark-mode widget base, mixed with the season color for the background. */
private val NightBase = Color(0xFF1C1B1F)

/**
 * The home-screen widget: the next event with its D-day, like the year view's
 * card, and (when made larger) the next few events.
 *
 * Sizes (it adapts as the user resizes it):
 * - small (2×1): title, D-day, date;
 * - wide (4×1): "다음 일정", title, when · who, D-day;
 * - large (4×2): the wide card, then three more events and a "+" (quick add).
 *
 * The background is a light tint of the current season's color. D-day badges
 * get stronger as the day comes closer (see `DdayStep`), exactly as in the app.
 */
class TandemWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, WIDE, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            // Read on every redraw, so an update shows the latest saved events and
            // "today" is always the real today.
            val state = WidgetStore.load(LocalContext.current)
            WidgetContent(state, LocalDate.now(), LocalTime.now())
        }
    }

    companion object {
        val SMALL = DpSize(110.dp, 40.dp)
        val WIDE = DpSize(250.dp, 40.dp)
        val LARGE = DpSize(250.dp, 140.dp)
    }
}

/** Registers the widget with Android, and starts/stops its background refresh. */
class TandemWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TandemWidget()

    /** The first widget was placed. */
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetUpdater.schedulePeriodic(context)
    }

    /** The last widget was removed. */
    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetUpdater.cancelAll(context)
    }
}

// ─────────────────────────────────────────────
// Colors
// ─────────────────────────────────────────────

/** Same color in light and dark mode. */
private fun fixed(color: Color) = ColorProvider(day = color, night = color)

private class WidgetColors(season: Season) {
    val background = ColorProvider(
        day = lerp(Color.White, season.color, 0.16f),
        night = lerp(NightBase, season.color, 0.14f)
    )
    val text = ColorProvider(day = Color(0xFF2B2622), night = Color(0xFFF3EFEA))
    val subtext = ColorProvider(day = Color(0xB32B2622), night = Color(0xB3F3EFEA))
    val divider = ColorProvider(
        day = lerp(Color.White, season.color, 0.45f),
        night = lerp(NightBase, season.color, 0.35f)
    )
    val plusBackground = fixed(season.color)
    val plusIcon = fixed(Color(0xFF2B2622))
}

// ─────────────────────────────────────────────
// Content
// ─────────────────────────────────────────────

@Composable
private fun WidgetContent(state: WidgetState, today: LocalDate, now: LocalTime) {
    val size = LocalSize.current
    val season = Season.of(today.monthValue)
    val colors = WidgetColors(season)
    val byId = state.items.associateBy { it.event.id }
    val upcoming = state.items.map { it.event }.upcoming(today, now).mapNotNull { byId[it.id] }
    val next = upcoming.firstOrNull()

    Box(
        GlanceModifier
            .fillMaxSize()
            .background(colors.background)
            .cornerRadius(20.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .clickable(openApp(next?.let { maxOf(it.event.date, today) })),
        contentAlignment = Alignment.CenterStart
    ) {
        when {
            !state.linked -> Message("공동 달력을 연결해 주세요", "앱에서 달력을 만들거나 코드로 참여해요", colors)
            next == null -> {
                val left = ChronoUnit.DAYS.between(today, seasonEnd(today))
                Message(
                    "다가오는 일정이 없어요",
                    if (left == 0L) "오늘이 ${season.label}의 마지막 날이에요" else "${season.label}이 ${left}일 남았어요",
                    colors
                )
            }
            size.width < TandemWidget.WIDE.width -> SmallCard(next, today, colors)
            size.height < TandemWidget.LARGE.height -> WideCard(next, today, colors)
            else -> Column(GlanceModifier.fillMaxSize()) {
                WideCard(next, today, colors)
                Spacer(GlanceModifier.height(8.dp))
                Box(GlanceModifier.fillMaxWidth().height(1.dp).background(colors.divider)) {}
                Spacer(GlanceModifier.height(4.dp))
                upcoming.drop(1).take(3).forEach { UpcomingRow(it, today, colors) }
                Spacer(GlanceModifier.defaultWeight())
                Row(GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                    PlusButton(colors)
                }
            }
        }
    }
}

/** Two lines of text, for the "not linked" and "nothing coming up" states. */
@Composable
private fun Message(title: String, detail: String, colors: WidgetColors) {
    Column {
        Text(title, style = textStyle(colors.text, 14.sp, FontWeight.Medium), maxLines = 1)
        Text(detail, style = textStyle(colors.subtext, 12.sp), maxLines = 1)
    }
}

/** 2×1: title and D-day on one line, the date below. */
@Composable
private fun SmallCard(item: WidgetItem, today: LocalDate, colors: WidgetColors) {
    val e = item.event
    Column(GlanceModifier.fillMaxWidth()) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                e.title,
                modifier = GlanceModifier.defaultWeight(),
                style = textStyle(colors.text, 15.sp, FontWeight.Medium),
                maxLines = 1
            )
            Spacer(GlanceModifier.width(6.dp))
            Badge(item, today, small = true)
        }
        Spacer(GlanceModifier.height(3.dp))
        Text(
            if (e.isMultiDay) periodLabel(e.date, e.lastDate) else shortWhen(e.date, e.time, today),
            style = textStyle(colors.subtext, 11.sp),
            maxLines = 1
        )
    }
}

/** 4×1: the app's D-day card — a bar in the author's color, three lines, the badge. */
@Composable
private fun WideCard(item: WidgetItem, today: LocalDate, colors: WidgetColors) {
    val e = item.event
    val ongoing = e.isMultiDay && e.date.isBefore(today)
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(GlanceModifier.width(3.dp).height(44.dp).background(fixed(item.color)).cornerRadius(2.dp)) {}
        Spacer(GlanceModifier.width(9.dp))
        Column(GlanceModifier.defaultWeight()) {
            Text(
                if (ongoing) "진행 중인 일정" else "다음 일정",
                style = textStyle(colors.subtext, 11.sp),
                maxLines = 1
            )
            Text(e.title, style = textStyle(colors.text, 15.sp, FontWeight.Medium), maxLines = 1)
            Text(
                "${eventWhenText(e, today)} · ${item.author}",
                style = textStyle(colors.subtext, 11.sp),
                maxLines = 1
            )
        }
        Spacer(GlanceModifier.width(8.dp))
        Badge(item, today, small = false)
    }
}

/** One line of the large widget's list: day · author dot · time · title. Opens that day. */
@Composable
private fun UpcomingRow(item: WidgetItem, today: LocalDate, colors: WidgetColors) {
    val e = item.event
    val ongoing = e.isMultiDay && e.date.isBefore(today)
    val day = when {
        ongoing -> "진행 중"
        e.date == today -> "오늘"
        e.date == today.plusDays(1) -> "내일"
        else -> "${e.date.monthValue}/${e.date.dayOfMonth}"
    }
    val time = when {
        e.isMultiDay -> "~ ${e.lastDate.monthValue}/${e.lastDate.dayOfMonth}"
        e.time.isBlank() -> "하루 종일"
        else -> displayTime(e.time)
    }
    Row(
        GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clickable(openApp(maxOf(e.date, today))),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(day, modifier = GlanceModifier.width(44.dp), style = textStyle(colors.text, 12.sp, FontWeight.Medium), maxLines = 1)
        Box(GlanceModifier.size(7.dp).background(fixed(item.color)).cornerRadius(4.dp)) {}
        Spacer(GlanceModifier.width(8.dp))
        Text(time, modifier = GlanceModifier.width(72.dp), style = textStyle(colors.subtext, 12.sp), maxLines = 1)
        Text(e.title, modifier = GlanceModifier.defaultWeight(), style = textStyle(colors.text, 12.sp), maxLines = 1)
    }
}

/** The D-day pill, with the app's stepped colors in light and dark mode. */
@Composable
private fun Badge(item: WidgetItem, today: LocalDate, small: Boolean) {
    val step = ddayStepOf(item.event, today)
    val (dayBg, dayFg) = ddayBadgeColors(item.color, step, dark = false)
    val (nightBg, nightFg) = ddayBadgeColors(item.color, step, dark = true)
    Box(
        GlanceModifier
            .background(ColorProvider(day = dayBg, night = nightBg))
            .cornerRadius(50.dp)
            .padding(horizontal = if (small) 8.dp else 11.dp, vertical = 3.dp)
    ) {
        Text(
            ddayText(item.event, today),
            style = textStyle(ColorProvider(day = dayFg, night = nightFg), if (small) 12.sp else 14.sp, FontWeight.Bold),
            maxLines = 1
        )
    }
}

/** Round "+" that opens the app with the quick-add bar focused. */
@Composable
private fun PlusButton(colors: WidgetColors) {
    Box(
        GlanceModifier
            .size(28.dp)
            .background(colors.plusBackground)
            .cornerRadius(14.dp)
            .clickable(actionStartActivity<MainActivity>(actionParametersOf(QuickAddKey to true))),
        contentAlignment = Alignment.Center
    ) {
        Text("+", style = textStyle(colors.plusIcon, 18.sp, FontWeight.Medium))
    }
}

// ─────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────

private fun textStyle(color: ColorProvider, size: TextUnit, weight: FontWeight = FontWeight.Normal) =
    TextStyle(color = color, fontSize = size, fontWeight = weight)

/** Opens the app, on [date]'s day when given. */
private fun openApp(date: LocalDate?): Action =
    if (date == null) actionStartActivity<MainActivity>()
    else actionStartActivity<MainActivity>(actionParametersOf(DateKey to date.toString()))

/** "오늘 · 오후 7:00", "내일", "11월 3일" for the small widget. */
private fun shortWhen(date: LocalDate, time: String, today: LocalDate): String {
    val day = when (date) {
        today -> "오늘"
        today.plusDays(1) -> "내일"
        else -> "${date.monthValue}월 ${date.dayOfMonth}일"
    }
    return if (time.isBlank()) day else "$day · ${displayTime(time)}"
}
