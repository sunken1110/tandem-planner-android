package io.github.sunken1110.tandemplanner.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import io.github.sunken1110.tandemplanner.FADE_DAYS

/**
 * One day cell, shared by the year grid and the month view.
 *
 * Visuals:
 * - Unfilled: a faint tint of the day's season color.
 * - Filled (past days, incl. today): a diagonal light-to-base gradient that
 *   fades in as the year fill animation passes this day.
 * - Today: a light sweep (shimmer) across the cell every few seconds.
 * - Dark mode: a soft radial glow behind filled cells.
 * - Selected: a primary-color outline (takes precedence over today's outline).
 *
 * Performance: all drawing happens in the draw phase via [drawWithCache], and
 * [fillProvider] is read only there, so the animation never triggers recomposition.
 *
 * @param fillProvider current fill progress in "days" (may be fractional).
 */
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
            // Today redraws every frame for the shimmer; isolate it in its own
            // layer so neighboring cells are not invalidated.
            .then(if (isToday) Modifier.graphicsLayer() else Modifier)
            // Build brushes/paths once per size change, then only draw per frame.
            .drawWithCache {
                val cr = CornerRadius(corner.toPx())
                val center = Offset(size.width / 2f, size.height / 2f)
                val glowRadius = size.maxDimension * 0.85f
                val glowBrush = if (dark) {
                    Brush.radialGradient(listOf(glow, Color.Transparent), center = center, radius = glowRadius)
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
                    // 0 = not reached yet, 1 = fully colored.
                    val p = if (dayOfYear <= passedDays) {
                        ((fillProvider() - (dayOfYear - 1)) / FADE_DAYS).coerceIn(0f, 1f)
                    } else 0f

                    if (glowBrush != null && p > 0f) {
                        drawCircle(glowBrush, radius = glowRadius, center = center, alpha = p)
                    }

                    drawRoundRect(faint, cornerRadius = cr)
                    if (p > 0f) drawRoundRect(brush, cornerRadius = cr, alpha = p)

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
