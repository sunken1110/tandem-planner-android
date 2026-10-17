package io.github.sunken1110.tandemplanner

import android.content.Context
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.sin
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

// ─────────────────────────────────────────────
// 테스트용 날짜. LocalDate.of(2026, 3, 1) 처럼 넣으면 그날인 것처럼 동작한다.
// 다른 계절의 파티클과 배너를 미리 볼 때 쓰고, 평소에는 null로 둔다.
// ─────────────────────────────────────────────
private val DEBUG_TODAY: LocalDate? = null

private val OnSeasonColor = Color(0xFF2B2B2B)   // 칠해진 칸 위의 기본 글자색
private val SundayRed = Color(0xFFE5484D)       // 일요일 숫자
private val SaturdaySky = Color(0xFF1E9BE8)     // 토요일 숫자 (하늘색)

private const val FADE_DAYS = 14f
private const val FILL_DURATION_MS = 2400

private val KoreanWeekdays = listOf("월", "화", "수", "목", "금", "토", "일")
private val CalendarHeader = listOf("일", "월", "화", "수", "목", "금", "토")

// ─────────────────────────────────────────────
// 로딩 → 인트로 → 메인 화면 전환 시간 (밀리초)
// ─────────────────────────────────────────────
private const val SPLASH_FADE_MS = 350L   // 시스템 로딩 화면이 사라지는 시간
private const val INTRO_IN_MS = 700       // 인트로 이미지가 서서히 나타나는 시간
private const val INTRO_HOLD_MS = 900L    // 인트로 이미지를 머물러 보여 주는 시간
private const val INTRO_OUT_MS = 700      // 인트로 → 메인 화면 교차 전환 시간

// ─────────────────────────────────────────────
// 햅틱 피드백 (짧은 진동)
// 화면(View)의 햅틱 기능은 폰의 "터치 진동" 설정이 꺼져 있거나 제조사 지원이 약하면
// 아무 반응이 없을 수 있어서, 진동 모터(Vibrator)를 직접 울리는 방식으로 바꿨다.
// ※ AndroidManifest.xml에 VIBRATE 권한이 꼭 있어야 한다.
// ─────────────────────────────────────────────
class Haptics(context: Context) {
    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    private fun play(effectId: Int, fallbackMs: Long, fallbackAmplitude: Int) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= 29) {
            v.vibrate(VibrationEffect.createPredefined(effectId))
        } else {
            v.vibrate(VibrationEffect.createOneShot(fallbackMs, fallbackAmplitude))
        }
    }

    /** 가벼운 탭: 버튼, 날짜 선택 */
    fun tap() = play(VibrationEffect.EFFECT_CLICK, 12, 120)
    /** 확인: 일정 저장 */
    fun confirm() = play(VibrationEffect.EFFECT_HEAVY_CLICK, 25, 255)
    /** 거절: 삭제, 빈 내용 저장 시도 (두 번 톡톡) */
    fun reject() = play(VibrationEffect.EFFECT_DOUBLE_CLICK, 40, 180)
}

@Composable
fun rememberHaptics(): Haptics {
    val context = LocalContext.current
    return remember(context) { Haptics(context.applicationContext) }
}

// ─────────────────────────────────────────────
// 계절 정의
// ─────────────────────────────────────────────
enum class Season(val label: String, val colors: List<Color>, val greeting: String) {
    SPRING("봄", listOf(Color(0xFFC5F27A), Color(0xFF6FD66B), Color(0xFF2EB872)), "새싹이 돋는 봄이 시작됐어요"),
    SUMMER("여름", listOf(Color(0xFF9BE7FF), Color(0xFF4FC3F7), Color(0xFF2196F3)), "맑은 하늘의 여름이 시작됐어요"),
    AUTUMN("가을", listOf(Color(0xFFFFE066), Color(0xFFFFB627), Color(0xFFF57C2B)), "단풍이 물드는 가을이 시작됐어요"),
    WINTER("겨울", listOf(Color(0xFFE6EBF1), Color(0xFFAEB9C8), Color(0xFFCBD5E1)), "은빛 겨울이 시작됐어요");

    val color: Color get() = colors[1]

    companion object {
        fun of(month: Int): Season = when (month) {
            in 3..5 -> SPRING
            in 6..8 -> SUMMER
            in 9..11 -> AUTUMN
            else -> WINTER
        }
    }
}

fun seasonStart(date: LocalDate): LocalDate = when (Season.of(date.monthValue)) {
    Season.SPRING -> LocalDate.of(date.year, 3, 1)
    Season.SUMMER -> LocalDate.of(date.year, 6, 1)
    Season.AUTUMN -> LocalDate.of(date.year, 9, 1)
    Season.WINTER -> LocalDate.of(if (date.monthValue == 12) date.year else date.year - 1, 12, 1)
}

fun seasonEnd(date: LocalDate): LocalDate = when (Season.of(date.monthValue)) {
    Season.SPRING -> LocalDate.of(date.year, 5, 31)
    Season.SUMMER -> LocalDate.of(date.year, 8, 31)
    Season.AUTUMN -> LocalDate.of(date.year, 11, 30)
    Season.WINTER -> YearMonth.of(if (date.monthValue == 12) date.year + 1 else date.year, 2).atEndOfMonth()
}

fun dayColor(date: LocalDate): Color {
    val c = Season.of(date.monthValue).colors
    val start = seasonStart(date)
    val length = ChronoUnit.DAYS.between(start, seasonEnd(date)).toFloat()
    val t = ChronoUnit.DAYS.between(start, date) / length
    return if (t < 0.5f) lerp(c[0], c[1], t * 2f) else lerp(c[1], c[2], (t - 0.5f) * 2f)
}

fun isSeasonFirstDay(date: LocalDate): Boolean =
    date.dayOfMonth == 1 && date.monthValue in listOf(3, 6, 9, 12)

/** 달력 열 번호(0=일 ~ 6=토)에 맞는 숫자 색 */
@Composable
fun dayNumberColor(column: Int, isPast: Boolean): Color = when (column) {
    0 -> SundayRed
    6 -> SaturdaySky
    else -> if (isPast) OnSeasonColor else MaterialTheme.colorScheme.onSurface
}

// 일정 데이터(Event)와 공동 달력 저장소(SharedCalendar)는 SharedCalendar.kt에 있다.

class MainActivity : ComponentActivity() {
    @SuppressLint("DiscouragedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        // 시스템 로딩 화면(스플래시). 반드시 super.onCreate보다 먼저 호출해야 한다.
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // ① 시스템 로딩 화면이 뚝 끊기지 않고 서서히 사라지게 한다
        splashScreen.setOnExitAnimationListener { provider ->
            provider.view.animate()
                .alpha(0f)
                .setDuration(SPLASH_FADE_MS)
                .withEndAction { provider.remove() }
                .start()
        }

        enableEdgeToEdge()

        // res/drawable 폴더에 splash_bg 이미지가 있으면 그 번호를, 없으면 0.
        // 이미지를 넣지 않아도 앱이 그대로 동작하게 하려고 이 방식을 쓴다.
        val introImage = resources.getIdentifier("splash_bg", "drawable", packageName)

        // 로딩 화면 배경색(colors.xml의 splash_background)을 인트로 바탕색으로도 쓴다.
        // 그래야 로딩 화면 → 인트로가 같은 색에서 이어져 튀지 않는다.
        val splashColorId = resources.getIdentifier("splash_background", "color", packageName)
        val splashColor = if (splashColorId != 0) Color(resources.getColor(splashColorId, theme)) else null

        setContent {
            val dark = isSystemInDarkTheme()
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                var showIntro by rememberSaveable { mutableStateOf(introImage != 0) }

                // 인트로가 사라지는 동안 메인 화면은 서서히 나타난다 (교차 전환)
                val mainAlpha by animateFloatAsState(
                    targetValue = if (showIntro) 0f else 1f,
                    animationSpec = tween(INTRO_OUT_MS),
                    label = "mainAlpha"
                )

                // 공동 달력 저장소 (앱 전체에서 하나만 사용)
                val calendar = remember { SharedCalendar(applicationContext) }

                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize().graphicsLayer { alpha = mainAlpha }) {
                            // 아직 공동 달력에 들어가지 않았으면 시작 화면, 들어갔으면 달력
                            Crossfade(
                                targetState = calendar.roomCode != null,
                                animationSpec = tween(500),
                                label = "setupOrCalendar"
                            ) { joined ->
                                if (joined) {
                                    // 인트로가 끝난 뒤에 칸 채우기 애니메이션이 시작된다
                                    YearProgressScreen(calendar = calendar, startFill = !showIntro)
                                } else {
                                    SetupScreen(calendar = calendar)
                                }
                            }
                        }

                        AnimatedVisibility(
                            visible = showIntro,
                            exit = fadeOut(tween(INTRO_OUT_MS)) +
                                    scaleOut(tween(INTRO_OUT_MS), targetScale = 1.04f)
                        ) {
                            IntroOverlay(
                                imageRes = introImage,
                                backgroundColor = splashColor ?: MaterialTheme.colorScheme.surface,
                                onFinished = { showIntro = false }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
// ② 인트로 화면
// 로딩 화면과 같은 바탕색 위에서 이미지가 서서히 떠오르며
// 살짝 줌아웃되고(정적인 사진에 움직임을 주는 효과), 잠시 머문 뒤 끝난다.
// 화면을 탭하면 바로 넘어간다.
// ─────────────────────────────────────────────
@Composable
fun IntroOverlay(imageRes: Int, backgroundColor: Color, onFinished: () -> Unit) {
    val imageAlpha = remember { Animatable(0f) }
    val imageScale = remember { Animatable(1.08f) }
    var finished by remember { mutableStateOf(false) }
    val finishOnce = {
        if (!finished) {
            finished = true
            onFinished()
        }
    }

    LaunchedEffect(Unit) {
        // 줌아웃은 나타나는 동안 + 머무는 동안 천천히 계속된다
        launch {
            imageScale.animateTo(
                1f,
                tween(INTRO_IN_MS + INTRO_HOLD_MS.toInt() + INTRO_OUT_MS, easing = LinearOutSlowInEasing)
            )
        }
        imageAlpha.animateTo(1f, tween(INTRO_IN_MS, easing = FastOutSlowInEasing))
        delay(INTRO_HOLD_MS.milliseconds)
        finishOnce()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { finishOnce() }
    ) {
        Image(
            painter = painterResource(imageRes),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = imageAlpha.value
                    scaleX = imageScale.value
                    scaleY = imageScale.value
                }
        )
    }
}

// ─────────────────────────────────────────────
// 메인 화면
// ─────────────────────────────────────────────
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun YearProgressScreen(calendar: SharedCalendar, startFill: Boolean = true) {
    var today by remember { mutableStateOf(DEBUG_TODAY ?: LocalDate.now()) }
    if (DEBUG_TODAY == null) {
        LaunchedEffect(today) {
            val untilMidnight = ChronoUnit.MILLIS.between(LocalDateTime.now(), today.plusDays(1).atStartOfDay())
            delay(untilMidnight.coerceAtLeast(1_000L).milliseconds)
            today = LocalDate.now()
        }
    }

    val year = today.year
    val totalDays = today.lengthOfYear()
    val passedDays = today.dayOfYear
    val season = Season.of(today.monthValue)
    val dark = isSystemInDarkTheme()
    val haptics = rememberHaptics()

    val yearColors = remember(year) { List(totalDays) { dayColor(LocalDate.ofYearDay(year, it + 1)) } }

    // 부드러운 채우기 애니메이션 (그리기 단계에서만 읽음)
    val fill = remember { Animatable(0f) }
    LaunchedEffect(passedDays, startFill) {
        if (!startFill) return@LaunchedEffect
        fill.animateTo(
            targetValue = passedDays + FADE_DAYS,
            animationSpec = tween(FILL_DURATION_MS, easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f))
        )
    }
    val fillProvider = remember { { fill.value } }
    val shownCount by remember(passedDays) { derivedStateOf { fill.value.toInt().coerceAtMost(passedDays) } }

    // ── 공동 일정 ──
    // 화면이 떠 있는 동안 서버의 일정을 실시간으로 듣고, 화면이 닫히면 듣기를 멈춘다.
    DisposableEffect(calendar.roomCode) {
        val listeners = calendar.startListening()
        onDispose { listeners.forEach { it.remove() } }
    }
    val eventsByDate by remember { derivedStateOf { calendar.events.groupBy { it.date } } }
    val colorOf: (String) -> Color = { calendar.colorOf(it) }
    val nameOf: (Event) -> String = { calendar.displayName(it.authorUid, it.authorName) }

    // 공유 창: 방금 달력을 만들었으면 자동으로 한 번 연다
    var showShare by remember { mutableStateOf(calendar.justCreated) }
    LaunchedEffect(Unit) { calendar.justCreated = false }

    // 예전에 폰에만 저장해 둔 일정이 있으면 옮길지 묻는다
    var localToMigrate by remember { mutableStateOf(calendar.pendingLocalEvents()) }

    var selectedMonth by rememberSaveable { mutableStateOf<Int?>(null) }
    BackHandler(enabled = selectedMonth != null) { selectedMonth = null }

    val bgTint by animateColorAsState(
        targetValue = dayColor(today).copy(alpha = if (dark) 0.08f else 0.12f),
        animationSpec = tween(800),
        label = "seasonBackground"
    )

    var bannerClosedOn by rememberSaveable { mutableStateOf<String?>(null) }
    val showBanner = isSeasonFirstDay(today) && bannerClosedOn != today.toString()

    Box(
        Modifier
            .fillMaxSize()
            .background(bgTint)
    ) {
        // 계절 파티클 (맨 뒤 배경)
        // 1년 보기에서는 오늘의 계절, 달을 확대하면 그 달의 계절 파티클로 부드럽게 바뀐다.
        // 같은 계절이면 상태가 그대로라 아무 변화가 없다.
        val particleSeason = selectedMonth?.let { Season.of(it) } ?: season
        Crossfade(targetState = particleSeason, animationSpec = tween(700), label = "particles") { s ->
            SeasonParticles(season = s, dark = dark)
        }

        // ── 워밍업 ──
        // 월 확대 화면 코드는 처음 실행될 때 안드로이드가 번역(컴파일)하느라 한 번 버벅인다.
        // 칸 채우기 애니메이션이 끝나면, 이번 달 확대 화면을 '보이지 않게' 몇 프레임 동안
        // 미리 그려 두어서 사용자가 처음 탭할 때도 매끄럽게 열리도록 한다.
        val fillDone by remember(passedDays) {
            derivedStateOf { fill.value >= passedDays + FADE_DAYS - 0.5f }
        }
        var prewarmed by remember { mutableStateOf(false) }
        if (fillDone && !prewarmed && selectedMonth == null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 0f }  // 완전히 투명하게
            ) {
                MonthDetail(
                    yearMonth = YearMonth.from(today),
                    today = today,
                    passedDays = passedDays,
                    fillProvider = fillProvider,
                    yearColors = yearColors,
                    eventsByDate = eventsByDate,
                    sharedScope = null,
                    animScope = null,
                    colorOf = colorOf,
                    nameOf = nameOf,
                    onAddEvent = { _, _, _ -> },
                    onDeleteEvent = {},
                    onBack = {},
                    onPrev = {},
                    onNext = {}
                )
            }
            LaunchedEffect(Unit) {
                repeat(3) { withFrameNanos { } }  // 3프레임만 그린 뒤
                prewarmed = true                  // 바로 치운다
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 14.dp, vertical = 16.dp)
        ) {
            AnimatedVisibility(
                visible = showBanner,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                SeasonBanner(season = season, onClose = {
                    haptics.tap()
                    bannerClosedOn = today.toString()
                })
            }

            // 공동 달력 표시줄: 함께 쓰는 사람 + 연결 상태
            SharedBar(
                calendar = calendar,
                onClick = {
                    haptics.tap()
                    showShare = true
                }
            )
            Spacer(Modifier.height(10.dp))

            YearHeader(today, season, shownCount, passedDays, totalDays, yearColors, fillProvider)
            Spacer(Modifier.height(14.dp))

            // [2] 달 카드가 그대로 커지는 전환
            SharedTransitionLayout(Modifier.fillMaxSize()) {
                val sharedScope = this
                AnimatedContent(
                    targetState = selectedMonth,
                    transitionSpec = {
                        val from = initialState
                        val to = targetState
                        when {
                            // 1년 보기 ↔ 월 보기: 크기 변화는 sharedBounds가 담당, 여기선 페이드만
                            from == null || to == null ->
                                fadeIn(tween(350)) togetherWith fadeOut(tween(250))
                            to > from ->
                                slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
                            else ->
                                slideInHorizontally { -it } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
                        }
                    },
                    label = "monthView"
                ) { month ->
                    val animScope = this
                    if (month == null) {
                        YearGrid(
                            year, today, passedDays, fillProvider, yearColors, eventsByDate,
                            sharedScope, animScope,
                            onMonthClick = {
                                haptics.tap()
                                selectedMonth = it
                            }
                        )
                    } else {
                        MonthDetail(
                            yearMonth = YearMonth.of(year, month),
                            today = today,
                            passedDays = passedDays,
                            fillProvider = fillProvider,
                            yearColors = yearColors,
                            eventsByDate = eventsByDate,
                            sharedScope = sharedScope,
                            animScope = animScope,
                            colorOf = colorOf,
                            nameOf = nameOf,
                            onAddEvent = { date, time, title -> calendar.addEvent(date, time, title) },
                            onDeleteEvent = { event -> calendar.deleteEvent(event) },
                            onBack = { selectedMonth = null },
                            onPrev = { if (month > 1) selectedMonth = month - 1 },
                            onNext = { if (month < 12) selectedMonth = month + 1 }
                        )
                    }
                }
            }
        }

        // 오류 안내 (화면 아래쪽에 잠깐 표시)
        val error = calendar.lastError
        AnimatedVisibility(
            visible = error != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    error.orEmpty(),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f, fill = false)
                )
                TextButton(onClick = { calendar.lastError = null }) { Text("닫기") }
            }
        }
        LaunchedEffect(error) {
            if (error != null) {
                delay(6_000.milliseconds)
                calendar.lastError = null
            }
        }
    }

    if (showShare) {
        ShareDialog(calendar = calendar, onDismiss = { showShare = false })
    }

    if (localToMigrate.isNotEmpty() && !showShare) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("이 폰에 저장된 일정이 있어요") },
            text = {
                Text("예전 버전에서 이 폰에만 저장한 일정 ${localToMigrate.size}개가 있어요. 공동 달력으로 옮길까요? 옮기면 함께 쓰는 사람도 볼 수 있어요.")
            },
            confirmButton = {
                TextButton(onClick = {
                    haptics.confirm()
                    calendar.migrateLocalEvents(upload = true)
                    localToMigrate = emptyList()
                }) { Text("옮기기") }
            },
            dismissButton = {
                TextButton(onClick = {
                    calendar.migrateLocalEvents(upload = false)
                    localToMigrate = emptyList()
                }) { Text("옮기지 않기") }
            }
        )
    }
}

// ─────────────────────────────────────────────
// 공동 달력 시작 화면
// 이름을 적고, 새 달력을 만들거나 코드로 참여한다.
// ─────────────────────────────────────────────
@Composable
fun SetupScreen(calendar: SharedCalendar) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val today = DEBUG_TODAY ?: LocalDate.now()
    val season = Season.of(today.monthValue)
    val dark = isSystemInDarkTheme()

    var name by rememberSaveable { mutableStateOf(calendar.myName) }
    var code by rememberSaveable { mutableStateOf("") }
    var nameError by remember { mutableStateOf(false) }
    var codeError by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun validName(): Boolean {
        nameError = name.isBlank()
        if (nameError) haptics.reject()
        return !nameError
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(dayColor(today).copy(alpha = if (dark) 0.08f else 0.12f))
    ) {
        SeasonParticles(season = season, dark = dark)

        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                Modifier
                    .size(width = 44.dp, height = 6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Brush.horizontalGradient(Season.entries.map { it.color }))
            )
            Spacer(Modifier.height(16.dp))
            Text("공동 달력 시작하기", fontSize = 26.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            Text(
                "한 사람이 달력을 만들고, 다른 사람은 코드를 입력해서 함께 써요.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(28.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(12); nameError = false },
                label = { Text("내 이름") },
                placeholder = { Text("하윤") },
                singleLine = true,
                isError = nameError,
                supportingText = {
                    Text(if (nameError) "이름을 입력해 주세요" else "일정에 누가 썼는지 표시돼요")
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(20.dp))

            // 새 달력 만들기
            Button(
                onClick = {
                    if (!validName() || busy) return@Button
                    haptics.tap()
                    busy = true
                    scope.launch {
                        val ok = calendar.createRoom(name.trim())
                        busy = false
                        if (ok) haptics.confirm() else haptics.reject()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) { Text("새 공동 달력 만들기", fontSize = 16.sp) }

            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                Text(
                    "또는 받은 코드로 참여",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp)
                )
                Box(Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
            }
            Spacer(Modifier.height(16.dp))

            // 코드로 참여하기
            OutlinedTextField(
                value = code,
                onValueChange = {
                    code = it.uppercase().filter { ch -> ch.isLetterOrDigit() }.take(6)
                    codeError = null
                },
                label = { Text("공유 코드 6자리") },
                placeholder = { Text("K7M2QX") },
                singleLine = true,
                isError = codeError != null,
                supportingText = {
                    val msg = codeError
                    if (msg != null) Text(msg) else Text("함께 쓸 사람에게 받은 코드")
                },
                textStyle = LocalTextStyle.current.copy(letterSpacing = 4.sp, fontWeight = FontWeight.Medium),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    if (!validName() || busy) return@OutlinedButton
                    if (code.length != 6) {
                        codeError = "코드 6자리를 입력해 주세요"
                        haptics.reject()
                        return@OutlinedButton
                    }
                    haptics.tap()
                    busy = true
                    scope.launch {
                        val ok = calendar.joinRoom(code, name.trim())
                        busy = false
                        if (ok) haptics.confirm() else {
                            codeError = calendar.lastError
                            calendar.lastError = null
                            haptics.reject()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) { Text("참여하기", fontSize = 16.sp) }

            Spacer(Modifier.height(20.dp))
            AnimatedVisibility(visible = busy) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("연결하는 중…", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // 만들기 실패 등 코드 칸과 관계없는 오류
            calendar.lastError?.let {
                Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

// ─────────────────────────────────────────────
// 상단 공동 달력 표시줄
// 함께 쓰는 사람의 색 점과 이름, 오프라인 여부를 보여 준다. 누르면 공유 창이 열린다.
// ─────────────────────────────────────────────
@Composable
fun SharedBar(calendar: SharedCalendar, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f))
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val members = calendar.members
            if (members.isEmpty()) {
                Text("공동 달력", fontSize = 13.sp, fontWeight = FontWeight.Medium)
            } else {
                members.forEachIndexed { i, uid ->
                    if (i > 0) Text("  ·  ", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(calendar.colorOf(uid))
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        calendar.displayName(uid, "") + if (uid == calendar.uid) " (나)" else "",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                if (members.size < 2) {
                    Text(
                        "  ·  초대하기",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        // 앱을 켠 직후 잠깐 '오프라인'으로 보이는 것을 막기 위해 2초 이상 지속될 때만 표시
        var showStatus by remember { mutableStateOf(false) }
        val busy = calendar.offline || calendar.pendingWrites
        LaunchedEffect(busy) {
            if (busy) {
                delay(2_000.milliseconds)
                showStatus = true
            } else {
                showStatus = false
            }
        }
        AnimatedVisibility(visible = showStatus) {
            Text(
                if (calendar.offline) "오프라인 · 연결되면 자동 저장" else "저장 중…",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────
// 공유 창: 코드 보여 주기 / 복사 / 공유, 함께 쓰는 사람, 내 이름 바꾸기
// ─────────────────────────────────────────────
@Composable
fun ShareDialog(calendar: SharedCalendar, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val code = calendar.roomCode.orEmpty()
    var copied by remember { mutableStateOf(false) }
    var editingName by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf(calendar.myName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("공동 달력") },
        text = {
            Column {
                Text(
                    if (calendar.members.size < 2) "함께 쓸 사람에게 이 코드를 알려 주세요."
                    else "두 사람이 함께 쓰고 있어요.",
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(14.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(code, fontSize = 30.sp, fontWeight = FontWeight.Medium, letterSpacing = 6.sp)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = {
                        val cm = context.getSystemService(ClipboardManager::class.java)
                        cm?.setPrimaryClip(ClipData.newPlainText("공동 달력 코드", code))
                        haptics.tap()
                        copied = true
                    }) { Text(if (copied) "복사됨" else "코드 복사") }
                    TextButton(onClick = {
                        haptics.tap()
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "공동 달력에 초대할게요! 앱에서 '코드로 참여'를 누르고 이 코드를 입력해 주세요: $code"
                            )
                        }
                        context.startActivity(Intent.createChooser(send, "코드 보내기"))
                    }) { Text("보내기") }
                }

                Spacer(Modifier.height(8.dp))
                Text("함께 쓰는 사람", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                calendar.members.forEach { uid ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                        Box(
                            Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(calendar.colorOf(uid))
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            calendar.displayName(uid, "") + if (uid == calendar.uid) " (나)" else "",
                            fontSize = 15.sp
                        )
                    }
                }
                if (calendar.members.size < 2) {
                    Text("아직 한 명만 있어요", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Spacer(Modifier.height(10.dp))
                if (editingName) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it.take(12) },
                        label = { Text("새 이름") },
                        singleLine = true,
                        isError = newName.isBlank(),
                        supportingText = if (newName.isBlank()) {
                            { Text("이름을 입력해 주세요") }
                        } else null
                    )
                    Row {
                        TextButton(onClick = { editingName = false; newName = calendar.myName }) { Text("취소") }
                        TextButton(onClick = {
                            if (newName.isBlank()) {
                                haptics.reject()
                            } else {
                                calendar.rename(newName.trim())
                                haptics.confirm()
                                editingName = false
                            }
                        }) { Text("저장") }
                    }
                } else {
                    TextButton(onClick = { editingName = true }) { Text("내 이름 바꾸기") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("확인") }
        }
    )
}

// ─────────────────────────────────────────────
// [1] 계절 파티클
// 봄: 꽃잎, 여름: 떠오르는 빛 방울, 가을: 낙엽, 겨울: 눈송이
// 시간 값을 '그리기 단계'에서만 읽어서 화면 구성에는 부담을 주지 않는다.
// ─────────────────────────────────────────────
private class Particle(
    val x: Float, val y: Float, val speed: Float, val size: Float,
    val drift: Float, val phase: Float, val spin: Float, val variant: Int
)

@Composable
fun SeasonParticles(season: Season, dark: Boolean) {
    val particles = remember(season) {
        val rnd = Random(season.ordinal * 31 + 7)
        List(26) {
            Particle(
                x = rnd.nextFloat(),
                y = rnd.nextFloat() * 1.2f,
                speed = 0.018f + rnd.nextFloat() * 0.03f,  // 화면 높이 대비 초당 이동 비율
                size = 0.6f + rnd.nextFloat() * 0.8f,
                drift = 0.01f + rnd.nextFloat() * 0.03f,
                phase = rnd.nextFloat() * 6.28f,
                spin = rnd.nextFloat() * 2f - 1f,
                variant = rnd.nextInt(2)
            )
        }
    }

    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { time.floatValue = (it - start) / 1_000_000_000f }
        }
    }

    // graphicsLayer(): 파티클만 별도 층에서 다시 그려서,
    // 매 프레임 움직이는 파티클 때문에 달력 전체가 다시 그려지지 않게 한다
    Canvas(
        Modifier
            .fillMaxSize()
            .graphicsLayer()
    ) {
        val t = time.floatValue
        val unit = 11.dp.toPx()
        particles.forEach { p ->
            val progress = (p.y + p.speed * t) % 1.2f - 0.1f
            val x = (p.x + sin(t * 0.6f + p.phase) * p.drift) * size.width
            val s = unit * p.size
            when (season) {
                Season.SPRING -> {
                    val y = progress * size.height
                    rotate(t * 40f * p.spin + p.phase * 57f, Offset(x, y)) {
                        drawOval(
                            color = Color(0xFFF8BBD0).copy(alpha = if (dark) 0.45f else 0.6f),
                            topLeft = Offset(x - s * 0.5f, y - s * 0.3f),
                            size = Size(s, s * 0.6f)
                        )
                    }
                }
                Season.SUMMER -> {
                    val y = (1f - progress) * size.height  // 아래에서 위로 떠오름
                    val c = Color(0xFF4FC3F7)
                    drawCircle(c.copy(alpha = 0.12f), radius = s * 0.5f, center = Offset(x, y))
                    drawCircle(
                        c.copy(alpha = if (dark) 0.5f else 0.4f), radius = s * 0.5f,
                        center = Offset(x, y), style = Stroke(1.dp.toPx())
                    )
                }
                Season.AUTUMN -> {
                    val y = progress * size.height
                    rotate(t * 60f * p.spin + p.phase * 57f, Offset(x, y)) {
                        drawOval(
                            color = season.colors[1 + p.variant].copy(alpha = if (dark) 0.5f else 0.55f),
                            topLeft = Offset(x - s * 0.55f, y - s * 0.28f),
                            size = Size(s * 1.1f, s * 0.55f)
                        )
                    }
                }
                Season.WINTER -> {
                    val y = progress * size.height
                    drawCircle(
                        color = if (dark) Color.White.copy(alpha = 0.6f) else Color(0xFF9FB0C4).copy(alpha = 0.55f),
                        radius = s * 0.22f,
                        center = Offset(x, y)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
// 계절 시작 배너
// ─────────────────────────────────────────────
@Composable
fun SeasonBanner(season: Season, onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.horizontalGradient(season.colors))
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("오늘부터 ${season.label}", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = OnSeasonColor)
            Text(season.greeting, fontSize = 13.sp, color = OnSeasonColor)
        }
        TextButton(onClick = onClose) { Text("닫기", color = OnSeasonColor) }
    }
}

// ─────────────────────────────────────────────
// 상단 헤더
// ─────────────────────────────────────────────
@Composable
fun YearHeader(
    today: LocalDate,
    season: Season,
    shownCount: Int,
    passedDays: Int,
    totalDays: Int,
    yearColors: List<Color>,
    fillProvider: () -> Float
) {
    Text("${today.year}년", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(verticalAlignment = Alignment.Bottom) {
        Text("${shownCount}일 지남", fontSize = 28.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(6.dp))
        Text(
            "/ ${totalDays}일 (${"%.1f".format(shownCount * 100f / totalDays)}%)",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp)
        )
    }
    Spacer(Modifier.height(8.dp))

    GradientProgressBar(yearColors, passedDays, totalDays, fillProvider)
    Spacer(Modifier.height(10.dp))

    val daysLeft = ChronoUnit.DAYS.between(today, seasonEnd(today))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(Brush.horizontalGradient(season.colors))
                .padding(horizontal = 10.dp, vertical = 3.dp)
        ) {
            Text(season.label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = OnSeasonColor)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            if (daysLeft == 0L) "오늘이 ${season.label}의 마지막 날이에요"
            else "${season.label}이 ${daysLeft}일 남았어요",
            fontSize = 13.sp
        )
    }
    Spacer(Modifier.height(10.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Season.entries.forEach {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(width = 18.dp, height = 10.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Brush.horizontalGradient(it.colors))
                )
                Spacer(Modifier.width(4.dp))
                Text(it.label, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun GradientProgressBar(yearColors: List<Color>, passedDays: Int, totalDays: Int, fillProvider: () -> Float) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val stops = remember(yearColors) {
        (0 until totalDays step 4).map { i -> (i / (totalDays - 1f)) to yearColors[i] }.toTypedArray()
    }
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
    ) {
        val radius = CornerRadius(size.height / 2, size.height / 2)
        drawRoundRect(color = trackColor, cornerRadius = radius)
        val progress = fillProvider().coerceAtMost(passedDays.toFloat())
        val filledWidth = size.width * progress / totalDays
        if (filledWidth > 0f) {
            clipRect(right = filledWidth) {
                drawRoundRect(
                    brush = Brush.horizontalGradient(*stops, startX = 0f, endX = size.width),
                    cornerRadius = radius
                )
            }
        }
    }
}

// ─────────────────────────────────────────────
// 날짜 칸 공통
// - 채워질 때 서서히 번지는 그라데이션
// - [4] 오늘 칸: 빛이 대각선으로 스치는 반짝임
// - [5] 다크 모드: 칠해진 칸 주변에 은은한 빛(glow)
// ─────────────────────────────────────────────
@Composable
fun DayCell(
    base: Color,
    dayOfYear: Int,
    passedDays: Int,
    fillProvider: () -> Float,
    isToday: Boolean,
    corner: Dp,
    borderWidth: Dp,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val dark = isSystemInDarkTheme()
    val brush = remember(base, dark) {
        Brush.linearGradient(listOf(lerp(base, Color.White, if (dark) 0.25f else 0.4f), base))
    }
    val glow = remember(base) { base.copy(alpha = 0.5f) }
    val faint = remember(base, dark) { base.copy(alpha = if (dark) 0.10f else 0.14f) }
    val outline = MaterialTheme.colorScheme.onSurface
    val selectedColor = MaterialTheme.colorScheme.primary

    val shimmer: State<Float>? = if (isToday) {
        rememberInfiniteTransition(label = "todayShimmer").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                tween(durationMillis = 1300, delayMillis = 1500, easing = LinearEasing),
                RepeatMode.Restart
            ),
            label = "shimmerProgress"
        )
    } else null

    Box(
        modifier = modifier
            .fillMaxSize()
            // 오늘 칸은 반짝임 때문에 매 프레임 다시 그려진다.
            // 별도 그리기 층(layer)을 줘서 오늘 칸만 다시 그리고, 나머지 칸은 건드리지 않게 한다.
            .then(if (isToday) Modifier.graphicsLayer() else Modifier)
            // drawWithCache: 크기가 정해지면 붓·경로·모서리 값을 한 번만 만들어 두고
            // 매 프레임에는 그리기만 한다 (예전엔 프레임마다 새로 만들었음)
            .drawWithCache {
                val cr = CornerRadius(corner.toPx())
                val c = Offset(size.width / 2f, size.height / 2f)
                val glowRadius = size.maxDimension * 0.85f
                val glowBrush = if (dark) {
                    Brush.radialGradient(listOf(glow, Color.Transparent), center = c, radius = glowRadius)
                } else null
                val clip = if (isToday) {
                    Path().apply { addRoundRect(RoundRect(Rect(Offset.Zero, size), cr)) }
                } else null
                val band = size.minDimension * 0.35f
                val half = (size.width + size.height) / 2f
                val shineColors = listOf(Color.Transparent, Color.White.copy(alpha = 0.7f), Color.Transparent)
                val bw = borderWidth.toPx()
                val strokeTopLeft = Offset(bw / 2, bw / 2)
                val strokeSize = Size(size.width - bw, size.height - bw)
                val stroke = Stroke(bw)

                onDrawBehind {
                    val p = if (dayOfYear <= passedDays) {
                        ((fillProvider() - (dayOfYear - 1)) / FADE_DAYS).coerceIn(0f, 1f)
                    } else 0f

                    // 다크 모드 glow: 칸 바깥으로 살짝 번지는 빛
                    if (glowBrush != null && p > 0f) {
                        drawCircle(glowBrush, radius = glowRadius, center = c, alpha = p)
                    }

                    drawRoundRect(faint, cornerRadius = cr)
                    if (p > 0f) drawRoundRect(brush, cornerRadius = cr, alpha = p)

                    // 오늘 칸 반짝임
                    if (shimmer != null && clip != null && p > 0f) {
                        val m = -band + (half + band * 2f) * shimmer.value
                        clipPath(clip) {
                            drawRect(
                                brush = Brush.linearGradient(
                                    shineColors,
                                    start = Offset(m - band, m - band),
                                    end = Offset(m + band, m + band)
                                ),
                                alpha = p
                            )
                        }
                    }

                    // 테두리: 선택한 날 > 오늘
                    val strokeColor = when {
                        isSelected -> selectedColor
                        isToday -> outline.copy(alpha = 0.85f)
                        else -> null
                    }
                    if (strokeColor != null) {
                        drawRoundRect(
                            color = strokeColor,
                            topLeft = strokeTopLeft,
                            size = strokeSize,
                            cornerRadius = cr,
                            style = stroke
                        )
                    }
                }
            },
        content = content
    )
}

// ─────────────────────────────────────────────
// 1년 보기
// ─────────────────────────────────────────────
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun YearGrid(
    year: Int,
    today: LocalDate,
    passedDays: Int,
    fillProvider: () -> Float,
    yearColors: List<Color>,
    eventsByDate: Map<LocalDate, List<Event>>,
    sharedScope: SharedTransitionScope,
    animScope: AnimatedVisibilityScope,
    onMonthClick: (Int) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items((1..12).toList()) { month ->
            val sharedModifier = with(sharedScope) {
                Modifier.sharedBounds(
                    sharedContentState = rememberSharedContentState(key = "month-$month"),
                    animatedVisibilityScope = animScope
                )
            }
            MonthCard(
                yearMonth = YearMonth.of(year, month),
                today = today,
                passedDays = passedDays,
                fillProvider = fillProvider,
                yearColors = yearColors,
                eventsByDate = eventsByDate,
                modifier = sharedModifier,
                onClick = { onMonthClick(month) }
            )
        }
    }
}

@Composable
fun MonthCard(
    yearMonth: YearMonth,
    today: LocalDate,
    passedDays: Int,
    fillProvider: () -> Float,
    yearColors: List<Color>,
    eventsByDate: Map<LocalDate, List<Event>>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val firstDayOffset = yearMonth.atDay(1).dayOfWeek.value % 7
    val daysInMonth = yearMonth.lengthOfMonth()
    val rows = (firstDayOffset + daysInMonth + 6) / 7

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
            .clickable(onClick = onClick)
            .padding(6.dp)
    ) {
        Text("${yearMonth.monthValue}월", fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(4.dp))

        Row(Modifier.fillMaxWidth()) {
            CalendarHeader.forEachIndexed { i, w ->
                Text(
                    w,
                    modifier = Modifier.weight(1f),
                    fontSize = 7.sp,
                    textAlign = TextAlign.Center,
                    color = when (i) {
                        0 -> SundayRed
                        6 -> SaturdaySky
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }

        for (r in 0 until rows) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                for (c in 0 until 7) {
                    val day = r * 7 + c - firstDayOffset + 1
                    Box(Modifier.weight(1f).aspectRatio(1f)) {
                        if (day in 1..daysInMonth) {
                            val date = yearMonth.atDay(day)
                            val doy = date.dayOfYear
                            DayCell(
                                base = yearColors[doy - 1],
                                dayOfYear = doy,
                                passedDays = passedDays,
                                fillProvider = fillProvider,
                                isToday = date == today,
                                corner = 3.dp,
                                borderWidth = 1.5.dp
                            ) {
                                if (eventsByDate[date]?.isNotEmpty() == true) {
                                    Box(
                                        Modifier
                                            .align(Alignment.Center)
                                            .size(3.dp)
                                            .clip(CircleShape)
                                            .background(OnSeasonColor.copy(alpha = 0.75f))
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
// 월 확대 보기 + 날짜별 일정
// ─────────────────────────────────────────────
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MonthDetail(
    yearMonth: YearMonth,
    today: LocalDate,
    passedDays: Int,
    fillProvider: () -> Float,
    yearColors: List<Color>,
    eventsByDate: Map<LocalDate, List<Event>>,
    sharedScope: SharedTransitionScope?,
    animScope: AnimatedVisibilityScope?,
    colorOf: (String) -> Color,
    nameOf: (Event) -> String,
    onAddEvent: (LocalDate, String, String) -> Unit,
    onDeleteEvent: (Event) -> Unit,
    onBack: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    val haptics = rememberHaptics()
    val season = Season.of(yearMonth.monthValue)
    val firstDayOffset = yearMonth.atDay(1).dayOfWeek.value % 7
    val daysInMonth = yearMonth.lengthOfMonth()
    val rows = (firstDayOffset + daysInMonth + 6) / 7
    val passedInMonth = (1..daysInMonth).count { yearMonth.atDay(it).dayOfYear <= passedDays }

    var selectedDay by rememberSaveable(yearMonth.monthValue) {
        mutableIntStateOf(if (YearMonth.from(today) == yearMonth) today.dayOfMonth else 1)
    }
    val selectedDate = yearMonth.atDay(selectedDay.coerceIn(1, daysInMonth))
    var showAddDialog by remember { mutableStateOf(false) }

    // 미리 실행(워밍업)용으로 그릴 때는 전환 효과 정보가 없으므로 그냥 넘어간다
    val sharedModifier = if (sharedScope != null && animScope != null) {
        with(sharedScope) {
            Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(key = "month-${yearMonth.monthValue}"),
                animatedVisibilityScope = animScope
            )
        }
    } else Modifier

    Column(
        Modifier
            .fillMaxSize()
            .then(sharedModifier)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .verticalScroll(rememberScrollState())
            .padding(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { haptics.tap(); onBack() }) { Text("‹ 1년 보기") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { haptics.tap(); onPrev() }, enabled = yearMonth.monthValue > 1) {
                Text("‹", fontSize = 20.sp)
            }
            Text("${yearMonth.monthValue}월", fontSize = 20.sp, fontWeight = FontWeight.Medium)
            TextButton(onClick = { haptics.tap(); onNext() }, enabled = yearMonth.monthValue < 12) {
                Text("›", fontSize = 20.sp)
            }
        }
        Text(
            "${season.label} · ${passedInMonth} / ${daysInMonth}일 지남",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp, bottom = 12.dp)
        )

        Row(Modifier.fillMaxWidth()) {
            CalendarHeader.forEachIndexed { i, w ->
                Text(
                    w,
                    modifier = Modifier.weight(1f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    color = when (i) {
                        0 -> SundayRed
                        6 -> SaturdaySky
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
        Spacer(Modifier.height(6.dp))

        for (r in 0 until rows) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (c in 0 until 7) {
                    val day = r * 7 + c - firstDayOffset + 1
                    Box(Modifier.weight(1f).aspectRatio(1f)) {
                        if (day in 1..daysInMonth) {
                            val date = yearMonth.atDay(day)
                            val doy = date.dayOfYear
                            val isPast = doy <= passedDays
                            val isToday = date == today
                            val dayEvents = eventsByDate[date].orEmpty()

                            DayCell(
                                base = yearColors[doy - 1],
                                dayOfYear = doy,
                                passedDays = passedDays,
                                fillProvider = fillProvider,
                                isToday = isToday,
                                isSelected = day == selectedDay,
                                corner = 10.dp,
                                borderWidth = 2.dp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        haptics.tap()
                                        selectedDay = day
                                    }
                            ) {
                                // 날짜 숫자: 왼쪽 위 모서리.
                                // 칠해진 칸에서는 아주 옅은 흰 배지를 깔아 숫자만 살짝 구분되게 한다.
                                Box(
                                    Modifier
                                        .align(Alignment.TopStart)
                                        .padding(start = 4.dp, top = 4.dp)
                                        .then(
                                            if (isPast) Modifier.background(
                                                Color.White.copy(alpha = 0.3f),
                                                RoundedCornerShape(3.dp)
                                            ) else Modifier
                                        )
                                        .padding(horizontal = 2.dp)
                                ) {
                                    Text(
                                        "$day",
                                        fontSize = 10.sp,
                                        lineHeight = 10.sp,
                                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                                        color = dayNumberColor(column = c, isPast = isPast)
                                    )
                                }
                                if (dayEvents.isNotEmpty()) {
                                    Row(
                                        Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(bottom = 5.dp),
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        // 일정마다 쓴 사람의 색 점 (최대 3개, 흰 테두리로 칸 색과 구분)
                                        dayEvents.take(3).forEach { ev ->
                                            Box(
                                                Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(Color.White.copy(alpha = 0.9f))
                                                    .padding(1.dp)
                                                    .clip(CircleShape)
                                                    .background(colorOf(ev.authorUid))
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 선택한 날의 일정 목록
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val weekdayIndex = selectedDate.dayOfWeek.value - 1  // 0=월 ~ 6=일
            Text(
                "${selectedDate.monthValue}월 ${selectedDate.dayOfMonth}일 (${KoreanWeekdays[weekdayIndex]})",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = when (weekdayIndex) {
                    6 -> SundayRed
                    5 -> SaturdaySky
                    else -> MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.padding(start = 4.dp)
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { haptics.tap(); showAddDialog = true }) { Text("+ 일정 추가") }
        }

        val dayEvents = eventsByDate[selectedDate].orEmpty().sortedBy { it.time.ifBlank { "99:99" } }
        val accent = yearColors[selectedDate.dayOfYear - 1]

        Column(Modifier.animateContentSize()) {
            if (dayEvents.isEmpty()) {
                Text(
                    "아직 일정이 없어요. \"+ 일정 추가\"로 적어 보세요.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 16.dp)
                )
            } else {
                dayEvents.forEach { e ->
                    EventRow(
                        event = e,
                        accent = accent,
                        authorName = nameOf(e),
                        authorColor = colorOf(e.authorUid),
                        onDelete = {
                            haptics.reject()
                            onDeleteEvent(e)
                        })
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showAddDialog) {
        AddEventDialog(
            date = selectedDate,
            onDismiss = { showAddDialog = false },
            onSave = { time, title ->
                haptics.confirm()
                onAddEvent(selectedDate, time, title)
                showAddDialog = false
            },
            onInvalid = { haptics.reject() }
        )
    }
}

@Composable
fun EventRow(event: Event, accent: Color, authorName: String, authorColor: Color, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(5.dp)
                .fillMaxHeight()
                .background(Brush.verticalGradient(listOf(lerp(accent, Color.White, 0.4f), accent)))
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 쓴 사람 이름표
                Text(
                    authorName,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = authorColor,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(authorColor.copy(alpha = 0.14f))
                        .padding(horizontal = 7.dp, vertical = 1.dp)
                )
                if (event.time.isNotBlank()) {
                    Spacer(Modifier.width(6.dp))
                    Text(event.time, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(event.title, fontSize = 15.sp)
        }
        TextButton(onClick = onDelete) { Text("삭제") }
    }
}

@Composable
fun AddEventDialog(
    date: LocalDate,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
    onInvalid: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${date.monthValue}월 ${date.dayOfMonth}일 일정") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it; showError = false },
                    label = { Text("일정 내용") },
                    placeholder = { Text("친구와 저녁 약속") },
                    singleLine = true,
                    isError = showError,
                    supportingText = if (showError) {
                        { Text("일정 내용을 입력해 주세요") }
                    } else null
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = time,
                    onValueChange = { time = it },
                    label = { Text("시간 (선택)") },
                    placeholder = { Text("18:30") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (title.isBlank()) {
                    showError = true
                    onInvalid()
                } else {
                    onSave(time.trim(), title.trim())
                }
            }) { Text("저장") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        }
    )
}