package io.github.sunken1110.tandemplanner.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.Image
import io.github.sunken1110.tandemplanner.ui.common.periodLabel
import io.github.sunken1110.tandemplanner.R
import androidx.glance.semantics.semantics
import androidx.glance.semantics.contentDescription
import androidx.glance.ColorFilter
import androidx.glance.ImageProvider
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.LocalAppWidgetOptions
import androidx.glance.LocalState
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.layout.ContentScale
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
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
import io.github.sunken1110.tandemplanner.data.Event
import io.github.sunken1110.tandemplanner.data.upcoming
import io.github.sunken1110.tandemplanner.model.Season
import io.github.sunken1110.tandemplanner.model.seasonEnd
import io.github.sunken1110.tandemplanner.ui.common.ddayBadgeColors
import io.github.sunken1110.tandemplanner.ui.common.ddayStepOf
import io.github.sunken1110.tandemplanner.ui.common.ddayText
import io.github.sunken1110.tandemplanner.ui.common.displayTime
import io.github.sunken1110.tandemplanner.ui.common.eventWhenText
import io.github.sunken1110.tandemplanner.ui.common.koreanShort
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
 * card, and (when made taller) the next few events.
 *
 * Sizes (it adapts as the user resizes it; 2×1 is the default):
 * - 1×1: a D-day tile — the whole tile takes the D-day step's color, from a faint
 *   tint (D-8+) to the author's full color (D-DAY), with the number large;
 * - 2×1: the app's D-day card, with short dates ("11/3 ~ 11/6 · 민지");
 * - 2×2: the card, the events after it and a "+" (quick add).
 * Stretched to 4 columns, the same layouts use the long dates of the app
 * ("11월 3일 (화) ~ 6일 (금) · 4일"); taller, the list fills the height with more
 * events (see [listRows]). The widget is drawn for its exact size ([SizeMode.Exact]).
 *
 * The background is a light tint of the current season's color. D-day badges
 * get stronger as the day comes closer (see `DdayStep`), exactly as in the app.
 */
open class TandemWidget : GlanceAppWidget() {

    // Drawn for the widget's actual size (not one of a few prepared sizes), so the
    // list can fill whatever height it's given. After a resize, the widget redraws
    // once the app has caught up (well under a second).
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Android's id for this placed widget: its background settings are saved under it.
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        provideContent {
            // Read on every redraw, so an update shows the latest saved events and
            // settings, and "today" is always the real today.
            val ctx = LocalContext.current
            // Glance only redraws a running widget when its own state changes, so every
            // refresh bumps this stamp (see WidgetUpdater.refresh); reading it here ties
            // the drawing to it. Without it, new settings or events showed only later.
            val stamp = (LocalState.current as? Preferences)?.get(RefreshStamp) ?: 0L
            val state = remember(stamp) { WidgetStore.load(ctx) }
            val style = remember(stamp) { WidgetStyleStore.load(ctx, appWidgetId) }
            // The photo is decoded only when a new one was saved.
            val source = remember(appWidgetId, style.background, style.photoStamp) {
                if (style.background == WidgetBackground.PHOTO) WidgetStyleStore.loadPhoto(ctx, appWidgetId) else null
            }
            // Cut to this widget's actual shape around the chosen spot; again after a resize.
            val size = LocalSize.current
            val aspect = WidgetStyleStore.widgetAspect(LocalAppWidgetOptions.current)
                ?: (size.width.value / size.height.value.coerceAtLeast(1f))
            val photo = remember(source, aspect, style.focusX, style.focusY, style.zoom) {
                source?.let { runCatching { PhotoCrop.crop(it, aspect, style) }.getOrNull() }
            }
            WidgetContent(state, style, photo, LocalDate.now(), LocalTime.now())
            // Tell the settings screen this refresh has been drawn.
            SideEffect { WidgetStyleStore.markRendered(ctx, appWidgetId, stamp) }
        }
    }

    companion object {
        /** 1×1. */
        val TILE = DpSize(40.dp, 40.dp)
        /** 2×1. */
        val CARD = DpSize(110.dp, 40.dp)
        /** 4×1: same card, long dates. */
        val CARD_WIDE = DpSize(250.dp, 40.dp)
        /** 2×2. */
        val LIST = DpSize(110.dp, 110.dp)
        /** 4×2: same list, long dates and separate time column. */
        val LIST_WIDE = DpSize(250.dp, 110.dp)

    }
}

/**
 * The three entries in the home screen's widget list. They are the same widget —
 * each can still be resized freely — but start at a different size and show a
 * matching preview in the list (`res/xml/tandem_widget_*_info.xml`).
 */
class TandemTileWidget : TandemWidget()
class TandemListWidget : TandemWidget()

/** Every kind of widget, for redrawing or counting all of them. */
val AllWidgetKinds = listOf(TandemWidget::class.java, TandemTileWidget::class.java, TandemListWidget::class.java)

/** Bumped on every refresh; the widget redraws when it changes. */
val RefreshStamp = longPreferencesKey("refresh_stamp")

/** 2×1 card (the original entry). Registers the widget and starts/stops its background refresh. */
open class TandemWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TandemWidget()

    /** The first widget was placed. */
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetUpdater.schedulePeriodic(context)
    }

    /** Widgets were removed: forget their background settings and photos. */
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        appWidgetIds.forEach { WidgetStyleStore.delete(context, it) }
    }

    /** The last widget of this kind was removed; stop the jobs if no kind is left. */
    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        if (!WidgetUpdater.anyWidgetPlaced(context)) WidgetUpdater.cancelAll(context)
    }
}

/** 1×1 D-day tile entry. */
class TandemTileWidgetReceiver : TandemWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TandemTileWidget()
}

/** 2×2 list entry. */
class TandemListWidgetReceiver : TandemWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TandemListWidget()
}

// ─────────────────────────────────────────────
// Colors
// ─────────────────────────────────────────────

/** Same color in light and dark mode. */
private fun fixed(color: Color) = ColorProvider(day = color, night = color)

/**
 * Text and accent colors for a widget, by its background:
 * - season color: dark text in light mode, light text in dark mode;
 * - photo, or a faint see-through backdrop: white text (over the darkening layer
 *   or the wallpaper), in both modes;
 * - a solid see-through backdrop: like the season color.
 */
private class WidgetColors(season: Season, style: WidgetStyle) {
    private val light = style.lightText
    val text = if (light) fixed(Color.White) else ColorProvider(day = Color(0xFF2B2622), night = Color(0xFFF3EFEA))

    /** [text] as plain colors, for the title view drawn outside Glance ([SteadyTitle]). */
    val textDay: Color = if (light) Color.White else Color(0xFF2B2622)
    val textNight: Color = if (light) Color.White else Color(0xFFF3EFEA)
    val subtext = if (light) fixed(Color(0xD9FFFFFF)) else ColorProvider(day = Color(0xB32B2622), night = Color(0xB3F3EFEA))
    /** Lighter still: the times in the list, so they read apart from the titles. */
    val muted = if (light) fixed(Color(0xB3FFFFFF)) else ColorProvider(day = Color(0x8C2B2622), night = Color(0x8CF3EFEA))
    val divider = when {
        style.background == WidgetBackground.SEASON ->
            ColorProvider(day = lerp(Color.White, season.color, 0.45f), night = lerp(NightBase, season.color, 0.35f))
        light -> fixed(Color(0x59FFFFFF))
        else -> ColorProvider(day = Color(0x332B2622), night = Color(0x33F3EFEA))
    }
    val plusBackground = fixed(season.color)
    val plusIcon = fixed(Color(0xFF2B2622))
}

/**
 * The widget's background, under its content:
 * - season: a light tint of the season's color (a dark one in dark mode);
 * - clear: white (black in dark mode) at [WidgetStyle.level] % over the wallpaper;
 * - photo: the photo, cropped to fill, with black at [WidgetStyle.level] % on top.
 */
@Composable
private fun BackgroundLayer(style: WidgetStyle, photo: Bitmap?, season: Season) {
    val alpha = style.level / 100f
    when (style.background) {
        WidgetBackground.PHOTO if photo != null -> {
            Image(
                provider = ImageProvider(photo),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = GlanceModifier.fillMaxSize()
            )
            Box(GlanceModifier.fillMaxSize().background(fixed(Color.Black.copy(alpha = alpha)))) {}
        }
        WidgetBackground.CLEAR -> Box(
            GlanceModifier.fillMaxSize().background(
                ColorProvider(day = Color.White.copy(alpha = alpha), night = Color.Black.copy(alpha = alpha))
            )
        ) {}
        else -> Box(
            GlanceModifier.fillMaxSize().background(
                ColorProvider(day = lerp(Color.White, season.color, 0.16f), night = lerp(NightBase, season.color, 0.14f))
            )
        ) {}
    }
}

// ─────────────────────────────────────────────
// Content
// ─────────────────────────────────────────────

@Composable
private fun WidgetContent(state: WidgetState, style: WidgetStyle, photo: Bitmap?, today: LocalDate, now: LocalTime) {
    val size = LocalSize.current
    val season = Season.of(today.monthValue)
    // A photo that couldn't be loaded shows the season color instead.
    val shownStyle = if (style.background == WidgetBackground.PHOTO && photo == null) {
        style.copy(background = WidgetBackground.SEASON)
    } else style
    val colors = WidgetColors(season, shownStyle)
    val byId = state.items.associateBy { it.event.id }
    val upcoming = state.items.map { it.event }.upcoming(today, now).mapNotNull { byId[it.id] }
    val next = upcoming.firstOrNull()
    val wide = size.width >= TandemWidget.CARD_WIDE.width
    val tile = size.width < TandemWidget.CARD.width

    // 1×1 with an event on the season background: the tile itself is the D-day badge.
    if (tile && state.linked && next != null && shownStyle.background == WidgetBackground.SEASON) {
        DdayTile(next, today)
        return
    }

    Box(
        GlanceModifier
            .fillMaxSize()
            .cornerRadius(20.dp)
            .clickable(openApp(next?.let { maxOf(it.event.date, today) }))
    ) {
        BackgroundLayer(shownStyle, photo, season)
        Box(
            GlanceModifier
                .fillMaxSize()
                .padding(horizontal = if (tile) 8.dp else 12.dp, vertical = 10.dp),
            contentAlignment = if (tile) Alignment.Center else Alignment.CenterStart
        ) {
        when {
            !state.linked -> Message("달력 연결 필요", "앱에서 달력을 만들거나 참여해요", colors)
            next == null -> {
                val left = ChronoUnit.DAYS.between(today, seasonEnd(today))
                Message(
                    "다가오는 일정 없음",
                    if (left == 0L) "오늘이 ${season.label}의 마지막 날" else "${season.label}이 ${left}일 남았어요",
                    colors
                )
            }
            // 1×1 on a photo or see-through backdrop: the D-day pill over it.
            tile -> PillTile(next, today, colors)
            size.height < TandemWidget.LIST.height -> Card(next, today, wide, colors)
            else -> Column(GlanceModifier.fillMaxSize()) {
                Card(next, today, wide, colors)
                Spacer(GlanceModifier.height(8.dp))
                Box(GlanceModifier.fillMaxWidth().height(1.dp).background(colors.divider)) {}
                Spacer(GlanceModifier.height(4.dp))
                val scale = LocalContext.current.resources.configuration.fontScale.coerceAtLeast(1f)
                val shown = upcoming.drop(1).take(listRows(size.height, scale))
                // Rows only where they fit: a very short widget shows the card and "+".
                // One day-column width for every row, so the dots and titles line up:
                // wider when any row shows today's time in it (narrow widgets only).
                val dayWidth = when {
                    wide -> 44.dp
                    shown.any { showsTimeAsDay(it.event, today) } -> TIME_COLUMN_NARROW
                    else -> 30.dp
                }
                // Glance keeps at most 10 children in one Column and silently drops the
                // rest, so the rows go in their own Column (in groups of 10); otherwise
                // the "+" below fell off once the list grew past a few rows.
                Column(GlanceModifier.fillMaxWidth()) {
                    shown.chunked(GLANCE_MAX_CHILDREN).forEach { group ->
                        Column(GlanceModifier.fillMaxWidth()) {
                            group.forEach { UpcomingRow(it, today, wide, dayWidth, colors) }
                        }
                    }
                }
                Spacer(GlanceModifier.defaultWeight())
                Row(GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                    PlusButton(colors)
                }
            }
        }
        }
    }
}

/** 1×1 on a photo or see-through backdrop: the D-day pill, with the title below. */
@Composable
private fun PillTile(item: WidgetItem, today: LocalDate, colors: WidgetColors) {
    val e = item.event
    val ongoing = e.isMultiDay && e.date.isBefore(today)
    Column(
        GlanceModifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Badge(item, today, small = false)
        Spacer(GlanceModifier.height(5.dp))
        Text(
            if (ongoing) "${ChronoUnit.DAYS.between(e.date, today) + 1}/${e.dayCount}일째" else e.title,
            style = textStyle(colors.text, 11.sp),
            maxLines = 1
        )
    }
}

/** Two lines of text, for the "not linked" and "nothing coming up" states. */
@Composable
private fun Message(title: String, detail: String, colors: WidgetColors) {
    Column {
        Text(title, style = textStyle(colors.text, 13.sp, FontWeight.Medium), maxLines = 2)
        Text(detail, style = textStyle(colors.subtext, 11.sp), maxLines = 2)
    }
}

/**
 * 1×1: the whole tile in the D-day step's color (faint far away, the author's
 * full color on the day), the D-day large, and the title — or "2/4일째" during a
 * trip — below.
 */
@Composable
private fun DdayTile(item: WidgetItem, today: LocalDate) {
    val e = item.event
    val step = ddayStepOf(e, today)
    val (dayBg, dayFg) = ddayBadgeColors(item.color, step, dark = false)
    val (nightBg, nightFg) = ddayBadgeColors(item.color, step, dark = true)
    val content = ColorProvider(day = dayFg, night = nightFg)
    val label = ddayText(e, today)
    val ongoing = e.isMultiDay && e.date.isBefore(today)
    Column(
        GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(day = dayBg, night = nightBg))
            .cornerRadius(20.dp)
            .padding(6.dp)
            .clickable(openApp(maxOf(e.date, today))),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Longer labels ("D-DAY", "진행 중", "D-120") get a slightly smaller size to fit.
        Text(
            label,
            style = textStyle(content, if (label.length <= 4) 22.sp else 18.sp, FontWeight.Bold),
            maxLines = 1
        )
        Spacer(GlanceModifier.height(3.dp))
        Text(
            if (ongoing) "${ChronoUnit.DAYS.between(e.date, today) + 1}/${e.dayCount}일째" else e.title,
            style = textStyle(content, 11.sp),
            maxLines = 1
        )
    }
}

/**
 * The app's D-day card: a bar in the author's color, "다음 일정", the title,
 * when · who, and the badge. [wide] uses the app's long dates; otherwise short ones.
 */
@Composable
private fun Card(item: WidgetItem, today: LocalDate, wide: Boolean, colors: WidgetColors) {
    val e = item.event
    val ongoing = e.isMultiDay && e.date.isBefore(today)
    val whenText = if (wide) eventWhenText(e, today) else shortWhen(e, today)
    // Each line sits at the top of a slot of fixed height (grown with the system
    // font size), so the lines sit at the same place in every widget size; the
    // title is drawn by SteadyTitle so it does too, whether it fits or is cut short.
    val scale = LocalContext.current.resources.configuration.fontScale.coerceAtLeast(1f)
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(GlanceModifier.width(3.dp).height(42.dp * scale).background(fixed(item.color)).cornerRadius(2.dp)) {}
        Spacer(GlanceModifier.width(8.dp))
        Column(GlanceModifier.defaultWeight()) {
            CardLine(SMALL_LINE * scale) {
                Text(if (ongoing) "진행 중인 일정" else "다음 일정", style = textStyle(colors.subtext, 11.sp), maxLines = 1)
            }
            CardLine(TITLE_LINE * scale) {
                SteadyTitle(e.title, colors)
            }
            CardLine(SMALL_LINE * scale) {
                Text("$whenText · ${item.author}", style = textStyle(colors.subtext, 11.sp), maxLines = 1)
            }
        }
        Spacer(GlanceModifier.width(6.dp))
        Badge(item, today, small = !wide)
    }
}

/**
 * One line of the list. Opens that day.
 *
 * - Wide widgets (4 columns): day · author dot · time · title. The time is in the
 *   lighter text color in a fixed-width column, so the titles line up.
 * - Narrow ones (2 columns): day · author dot · title, leaving the title room;
 *   the next event's time is already in the card above. Today's events show
 *   their time in place of the day ("오후 3:00 ● 치과"), since that's what
 *   matters today; then every row's day column widens ([dayWidth]) so the dots
 *   and titles still line up.
 *
 * Times read as in the app ("오후 7:00", "하루 종일"); multi-day events show
 * "~2/12". Events without a time (all day, or several days) get a hollow dot,
 * so they stand apart without using any width. TalkBack reads the whole row
 * with its full date and time, also where the time isn't shown.
 */
@Composable
private fun UpcomingRow(item: WidgetItem, today: LocalDate, wide: Boolean, dayWidth: Dp, colors: WidgetColors) {
    val e = item.event
    val ongoing = e.isMultiDay && e.date.isBefore(today)
    val timed = !e.isMultiDay && e.time.isNotBlank()
    // Narrow: today's timed events show the time where the day would be.
    val timeAsDay = !wide && showsTimeAsDay(e, today)
    val day = when {
        timeAsDay -> displayTime(e.time)
        ongoing -> "진행"
        e.date == today -> "오늘"
        e.date == today.plusDays(1) -> "내일"
        else -> "${e.date.monthValue}/${e.date.dayOfMonth}"
    }
    // A fixed height (grown with the system font size), so rows sit at the same
    // place in every widget size even though wide ones use a larger font.
    val fontScale = LocalContext.current.resources.configuration.fontScale
    Row(
        GlanceModifier
            .fillMaxWidth()
            .height(ROW_HEIGHT * fontScale.coerceAtLeast(1f))
            .clickable(openApp(maxOf(e.date, today)))
            .semantics { contentDescription = rowDescription(item, today) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            day,
            modifier = GlanceModifier.width(dayWidth),
            style = textStyle(colors.text, 11.sp, FontWeight.Medium),
            maxLines = 1
        )
        AuthorDot(item, hollow = !timed)
        Spacer(GlanceModifier.width(6.dp))
        val size = if (wide) 12.sp else 11.sp
        if (wide) {
            Text(
                when {
                    e.isMultiDay -> "~${e.lastDate.monthValue}/${e.lastDate.dayOfMonth}"
                    e.time.isBlank() -> "하루 종일"
                    else -> displayTime(e.time)
                },
                // Wide enough for "오후 12:30" and "하루 종일".
                modifier = GlanceModifier.width(72.dp),
                style = textStyle(colors.muted, size),
                maxLines = 1
            )
        }
        Text(e.title, modifier = GlanceModifier.defaultWeight(), style = textStyle(colors.text, size), maxLines = 1)
    }
}

/** Today's timed event: a narrow list writes its time where the day would go. */
private fun showsTimeAsDay(e: Event, today: LocalDate): Boolean =
    !e.isMultiDay && e.time.isNotBlank() && e.date == today

/**
 * How many list rows fit under the card in a widget [height] tall (its actual
 * height), at the system font [scale]: the height minus padding, the card, the
 * divider, the "+" button and [SAFETY_MARGIN], divided by a row's height.
 * Depends on the height only, so widgets of the same height show the same
 * number of events.
 */
private fun listRows(height: Dp, scale: Float): Int {
    val fixed = 20.dp + (SMALL_LINE * 2 + TITLE_LINE) * scale + 13.dp + 28.dp + SAFETY_MARGIN
    return ((height - fixed) / (ROW_HEIGHT * scale)).toInt().coerceAtLeast(0)
}

/**
 * Kept free when counting rows. Launchers don't always report a widget's size
 * exactly; if one reports a little more than it shows, a row too many would
 * push the "+" out of sight. Better one row less than no "+".
 */
private val SAFETY_MARGIN = 8.dp

/** Most children Glance keeps in one Row, Column or Box (the rest are dropped). */
private const val GLANCE_MAX_CHILDREN = 10

/** The author's color dot; [hollow] (a ring) for events without a time. */
@Composable
private fun AuthorDot(item: WidgetItem, hollow: Boolean) {
    if (hollow) {
        Image(
            provider = ImageProvider(R.drawable.widget_dot_ring),
            contentDescription = null,
            colorFilter = ColorFilter.tint(fixed(item.color)),
            modifier = GlanceModifier.size(6.dp)
        )
    } else {
        Box(GlanceModifier.size(6.dp).background(fixed(item.color)).cornerRadius(3.dp)) {}
    }
}

/** What TalkBack reads for a list row: "2월 14일 (토) 오후 3:00, 발렌타인데이, 하윤". */
private fun rowDescription(item: WidgetItem, today: LocalDate): String {
    val e = item.event
    val whenText = when {
        e.isMultiDay -> periodLabel(e.date, e.lastDate, weekdays = true) +
            if (e.date.isBefore(today)) " 진행 중" else ""
        else -> {
            val date = "${e.date.monthValue}월 ${e.date.dayOfMonth}일 (${e.date.dayOfWeek.koreanShort()})"
            "$date " + if (e.time.isBlank()) "하루 종일" else displayTime(e.time)
        }
    }
    return "$whenText, ${e.title}, ${item.author}"
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

/**
 * Short "when" for narrow widgets: "11/3 ~ 11/6", "11/3 ~ 11/6 · 2/4일째" during a
 * trip, "오늘 · 오후 7:00", "내일 · 하루 종일", "11/5 (목) · 오후 2:00" (separated
 * like the wide card's).
 */
private fun shortWhen(e: Event, today: LocalDate): String {
    if (e.isMultiDay) {
        // Period first, then how far along, like the wide card's "11월 3일 ~ 6일 · 2/4일째".
        val period = "${e.date.monthValue}/${e.date.dayOfMonth} ~ ${e.lastDate.monthValue}/${e.lastDate.dayOfMonth}"
        return if (e.date.isBefore(today)) "$period · ${ChronoUnit.DAYS.between(e.date, today) + 1}/${e.dayCount}일째"
        else period
    }
    val day = when (e.date) {
        today -> "오늘"
        today.plusDays(1) -> "내일"
        else -> "${e.date.monthValue}/${e.date.dayOfMonth} (${e.date.dayOfWeek.koreanShort()})"
    }
    return "$day · " + if (e.time.isBlank()) "하루 종일" else displayTime(e.time)
}

/** One line of the card: [content] pinned to the top of a slot [height] tall. */
@Composable
private fun CardLine(height: Dp, content: @Composable () -> Unit) {
    Box(GlanceModifier.fillMaxWidth().height(height), contentAlignment = Alignment.TopStart) {
        content()
    }
}

/**
 * Card line heights at the normal font size: 11sp lines (room for Korean text
 * cut short with "…", which lays out a little taller) and the 14sp title.
 */
private val SMALL_LINE = 16.dp
private val TITLE_LINE = 19.dp

/**
 * The card's title, drawn with a plain Android TextView instead of Glance's Text.
 *
 * A one-line TextView lays text out one way when it fits and another way when it
 * is cut short with "…", and the two put Korean text (drawn with a taller
 * fallback font) at slightly different heights: the same title sat ~1.5dp lower
 * in a wide widget, where it fit, than in a narrow one, where it was cut. Glance
 * can't change how the TextView measures lines, so this view turns off the font
 * padding and the fallback line spacing (`res/layout/widget_card_title.xml`):
 * both ways then use the same font measurements and the title stays put.
 */
@Composable
private fun SteadyTitle(text: String, colors: WidgetColors) {
    val context = LocalContext.current
    val views = RemoteViews(context.packageName, R.layout.widget_card_title).apply {
        setTextViewText(R.id.widget_card_title, text)
        // Day and night colors, so the text follows the theme like Glance's own.
        setColorInt(R.id.widget_card_title, "setTextColor", colors.textDay.toArgb(), colors.textNight.toArgb())
    }
    AndroidRemoteViews(views, GlanceModifier.fillMaxWidth())
}


/**
 * Height of a list row at the normal font size: the 12sp text of wide widgets
 * plus a little air. Every size uses it, so the rows line up across sizes.
 */
private val ROW_HEIGHT = 22.dp

/**
 * Day column of a narrow-list row that shows today's time instead
 * ("오후 12:30" at most), written as in the app.
 */
private val TIME_COLUMN_NARROW = 56.dp
