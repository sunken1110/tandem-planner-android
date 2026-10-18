package io.github.sunken1110.tandemplanner.ui.effects

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import io.github.sunken1110.tandemplanner.model.Season
import kotlin.math.sin
import kotlin.random.Random

/** Number of particles on screen. Lower it to save battery. */
private const val PARTICLE_COUNT = 26

private class Particle(
    val x: Float,      // horizontal position, 0..1 of width
    val y: Float,      // initial vertical phase, 0..1.2
    val speed: Float,  // screen heights per second
    val size: Float,   // size multiplier
    val drift: Float,  // horizontal sway amplitude, fraction of width
    val phase: Float,  // sway / rotation phase offset
    val spin: Float,   // rotation direction and speed, -1..1
    val variant: Int   // alternate color index
)

/**
 * Ambient seasonal background.
 *
 * Spring: falling petals · Summer: rising light bubbles ·
 * Autumn: falling leaves · Winter: snowflakes.
 *
 * Time is read only in the draw phase and the canvas has its own layer, so the
 * per-frame animation never recomposes or redraws the calendar above it.
 */
@Composable
fun SeasonParticles(season: Season, dark: Boolean) {
    val particles = remember(season) {
        val rnd = Random(season.ordinal * 31 + 7) // stable layout per season
        List(PARTICLE_COUNT) {
            Particle(
                x = rnd.nextFloat(),
                y = rnd.nextFloat() * 1.2f,
                speed = 0.018f + rnd.nextFloat() * 0.03f,
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

    Canvas(
        Modifier
            .fillMaxSize()
            .graphicsLayer()
    ) {
        val t = time.floatValue
        val unit = 11.dp.toPx()
        particles.forEach { p ->
            // Loops from just above the top (-0.1) to just below the bottom (1.1).
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
                    val y = (1f - progress) * size.height // rises upward
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
