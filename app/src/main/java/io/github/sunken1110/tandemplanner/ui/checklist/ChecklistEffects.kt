package io.github.sunken1110.tandemplanner.ui.checklist

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import io.github.sunken1110.tandemplanner.ui.common.colorOf
import io.github.sunken1110.tandemplanner.data.SharedChecklists
import io.github.sunken1110.tandemplanner.data.SharedCalendar
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sunken1110.tandemplanner.model.Season
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** How long the completion burst flies. */
private const val BURST_MS = 1300

/** Number of pieces in the completion burst. */
private const val BURST_PIECES = 30

/**
 * The list's progress bar. Its fill glides to each new value; once everything is
 * checked it turns green, and a light runs across it every time [shineToken]
 * changes (the moment the list is finished).
 */
@Composable
fun ChecklistProgressBar(
    fraction: Float,
    complete: Boolean,
    season: Season,
    shineToken: Int,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp
) {
    val fill by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 140f),
        label = "progressFill"
    )
    val start by animateColorAsState(if (complete) lerp(CompleteGreen, Color.White, 0.3f) else season.colors[0], tween(500), label = "barStart")
    val end by animateColorAsState(if (complete) CompleteGreen else season.colors[2], tween(500), label = "barEnd")
    val shine = remember { Animatable(-1f) }
    LaunchedEffect(shineToken) {
        if (shineToken == 0) return@LaunchedEffect
        shine.snapTo(0f)
        shine.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
        shine.snapTo(-1f)
    }
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(50))
            .drawBehind {
                val r = CornerRadius(size.height / 2)
                drawRoundRect(track, cornerRadius = r)
                val w = size.width * fill
                if (w > 0f) {
                    drawRoundRect(
                        Brush.horizontalGradient(listOf(start, end), endX = size.width),
                        size = Size(w, size.height),
                        cornerRadius = r
                    )
                }
                val s = shine.value
                if (s >= 0f) {
                    val x = -size.width * 0.3f + s * size.width * 1.6f
                    drawRect(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, Color.White.copy(alpha = 0.75f), Color.Transparent),
                            startX = x - size.width * 0.15f,
                            endX = x + size.width * 0.15f
                        ),
                        size = Size(w, size.height)
                    )
                }
            }
    )
}

private class Piece(
    val angle: Float,
    val speed: Float,
    val spin: Float,
    val size: Float,
    val color: Color,
    /** Start of a piece's side-to-side sway, so they don't all sway together. */
    val phase: Float
)

/**
 * A short burst from the center of this box each time [token] changes (but not on
 * first composition), shaped by [season] like the background particles: spring
 * petals and autumn leaves drift down swaying, summer light bubbles float up,
 * winter snowflakes turn as they fall. Draws outside its own bounds, so a small
 * box under the progress bar is enough.
 */
@Composable
fun CelebrationBurst(token: Int, season: Season, modifier: Modifier = Modifier) {
    val t = remember { Animatable(1f) }
    val pieces = remember(token, season) {
        val rnd = Random(token * 7919 + 13)
        val colors = season.colors + Color.White
        List(BURST_PIECES) {
            Piece(
                // Mostly upward, fanning out to both sides.
                angle = (-PI / 2 + (rnd.nextFloat() - 0.5f) * PI * 1.2f).toFloat(),
                speed = 0.55f + rnd.nextFloat() * 0.6f,
                spin = (rnd.nextFloat() - 0.5f) * 4f,
                size = 0.8f + rnd.nextFloat() * 0.7f,
                color = colors[rnd.nextInt(colors.size)],
                phase = rnd.nextFloat() * 2f * PI.toFloat()
            )
        }
    }
    LaunchedEffect(token) {
        if (token == 0) return@LaunchedEffect
        t.snapTo(0f)
        t.animateTo(1f, tween(BURST_MS, easing = LinearEasing))
    }
    Canvas(modifier) {
        val p = t.value
        if (p >= 1f) return@Canvas
        val reach = 170.dp.toPx()
        val fall = 220.dp.toPx()
        val sway = 12.dp.toPx()
        val base = 5.dp.toPx()
        val origin = Offset(size.width / 2, size.height / 2)
        // Ease out the launch, then let the season's motion take over.
        val travel = 1f - (1f - p) * (1f - p)
        val alpha = (1f - p * p).coerceIn(0f, 1f)
        pieces.forEach { piece ->
            val swayX = when (season) {
                Season.SPRING, Season.AUTUMN -> sin(p * 9f + piece.phase) * sway * p
                else -> 0f
            }
            val drift = when (season) {
                Season.SUMMER -> -fall * 0.25f * p // light rises
                Season.WINTER -> fall * 0.45f * p * p // snow falls slowly
                else -> fall * 0.6f * p * p
            }
            val center = Offset(
                origin.x + cos(piece.angle) * reach * piece.speed * travel + swayX,
                origin.y + sin(piece.angle) * reach * piece.speed * travel + drift
            )
            val s = base * piece.size
            val color = piece.color.copy(alpha = alpha)
            val turn = piece.spin * 180f * p + if (season == Season.WINTER) p * 120f else 0f
            rotate(degrees = turn, pivot = center) {
                when (season) {
                    Season.SPRING -> drawOval(
                        color,
                        topLeft = Offset(center.x - s * 1.1f, center.y - s * 0.55f),
                        size = Size(s * 2.2f, s * 1.1f)
                    )
                    Season.SUMMER -> {
                        drawCircle(color, radius = s * 0.8f, center = center, style = Stroke(1.4.dp.toPx()))
                        drawCircle(color.copy(alpha = alpha * 0.5f), radius = s * 0.22f, center = center + Offset(-s * 0.25f, -s * 0.25f))
                    }
                    Season.AUTUMN -> {
                        val leaf = Path().apply {
                            moveTo(center.x, center.y - s * 1.2f)
                            quadraticTo(center.x + s, center.y - s * 0.2f, center.x, center.y + s * 1.2f)
                            quadraticTo(center.x - s, center.y - s * 0.2f, center.x, center.y - s * 1.2f)
                        }
                        drawPath(leaf, color)
                        drawLine(
                            Color(0xFF854F0B).copy(alpha = alpha * 0.6f),
                            Offset(center.x, center.y - s),
                            Offset(center.x, center.y + s * 1.2f),
                            0.8.dp.toPx()
                        )
                    }
                    Season.WINTER -> repeat(3) { arm ->
                        val a = arm * PI.toFloat() / 3f
                        drawLine(
                            color,
                            Offset(center.x - cos(a) * s, center.y - sin(a) * s),
                            Offset(center.x + cos(a) * s, center.y + sin(a) * s),
                            1.4.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }
            }
        }
    }
}

/** "민지 보는 중" with a softly breathing dot in their color. */
@Composable
fun ViewingBadge(name: String, color: Color, reduceMotion: Boolean, modifier: Modifier = Modifier) {
    // Read only while drawing the dot (below), so breathing doesn't recompose the badge.
    val breathing = if (reduceMotion) {
        null
    } else {
        val transition = rememberInfiniteTransition(label = "viewing")
        transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "breath"
        )
    }
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(7.dp)
                .graphicsLayer {
                    val breath = breathing?.value ?: 1f
                    alpha = breath
                    scaleX = 0.8f + 0.2f * breath
                    scaleY = 0.8f + 0.2f * breath
                }
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(5.dp))
        Text("$name 보는 중", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = color, maxLines = 1)
    }
}

/**
 * "민지 보는 중" for [listId] when the other member has it open; nothing otherwise.
 * Re-checks the presence notes every few seconds on its own, so a closed app's
 * note expires; only this badge redraws then, not the screen around it.
 */
@Composable
fun ViewingNow(
    checklists: SharedChecklists,
    calendar: SharedCalendar,
    listId: String,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier
) {
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(PRESENCE_RECHECK)
            nowMs = System.currentTimeMillis()
        }
    }
    val viewer = checklists.viewersOf(listId, nowMs).firstOrNull() ?: return
    ViewingBadge(calendar.displayName(viewer, "상대"), calendar.colorOf(viewer), reduceMotion, modifier)
}

/** How often "보는 중" is re-checked against the clock. */
private val PRESENCE_RECHECK = 5.seconds

/**
 * A short message at the bottom of the sheet, optionally with an action
 * ("되돌리기"). [token] makes each message distinct, so the same text twice
 * still restarts its timer.
 */
data class SheetToast(
    val message: String,
    val actionLabel: String? = null,
    val token: Long = System.nanoTime(),
    val onAction: (() -> Unit)? = null
)

@Composable
fun SheetToastView(toast: SheetToast, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.inverseSurface,
        shadowElevation = 4.dp,
        modifier = modifier
    ) {
        Row(
            Modifier.padding(start = 14.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                toast.message,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(vertical = 10.dp)
            )
            val action = toast.onAction
            if (toast.actionLabel != null && action != null) {
                TextButton(onClick = {
                    action()
                    onDismiss()
                }) {
                    Text(toast.actionLabel, color = MaterialTheme.colorScheme.inversePrimary, fontWeight = FontWeight.Medium)
                }
            } else {
                Spacer(Modifier.width(10.dp))
            }
        }
    }
}
