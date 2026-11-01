package io.github.sunken1110.tandemplanner.ui.month

import android.graphics.BlurMaskFilter
import android.graphics.Matrix
import android.graphics.SweepGradient
import android.os.Build
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.sunken1110.tandemplanner.USE_GPU_GLOW
import io.github.sunken1110.tandemplanner.model.Season
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import android.graphics.Paint as FrameworkPaint

/**
 * How the month view's selection ring is drawn.
 * - [SOLID] (light mode): a ring in the vivid season color with a glow in the same
 *   hue. While it breathes, the ring brightens toward white and back.
 * - [NEON] (dark mode): a near-white core with a vivid glow, like a lit neon tube.
 */
enum class RingStyle { SOLID, NEON }

/**
 * Ring colors. For [RingStyle.SOLID] [core] is the deep season color;
 * for [RingStyle.NEON] it is near-white. [glow] is the vivid season color in both.
 */
data class RingColors(val core: Color, val glow: Color)

/** Ring style for the current theme. */
fun ringStyleFor(dark: Boolean): RingStyle = if (dark) RingStyle.NEON else RingStyle.SOLID

/**
 * Ring colors per season and style. Solid colors share the season's hue but are
 * much deeper than its cell colors, so the ring never blends into the cells.
 */
fun ringColorsOf(season: Season, style: RingStyle): RingColors {
    val glow = when (season) {
        Season.SPRING -> Color(0xFF46C850) // vivid green
        Season.SUMMER -> Color(0xFF28A0F0) // vivid blue
        Season.AUTUMN -> Color(0xFFFF8A1E) // vivid orange
        Season.WINTER -> Color(0xFF8CAAD7) // silver blue
    }
    val core = when (style) {
        // The vivid color, optionally mixed toward white (see LIGHT_RING_REST_WHITE).
        RingStyle.SOLID -> lerp(glow, Color.White, LIGHT_RING_REST_WHITE)
        RingStyle.NEON -> when (season) {
            Season.SPRING -> Color(0xFFEFFFE8)
            Season.SUMMER -> Color(0xFFE8F7FF)
            Season.AUTUMN -> Color(0xFFFFF3DE)
            Season.WINTER -> Color(0xFFF2F6FB)
        }
    }
    return RingColors(core = core, glow = glow)
}



/** Neon: glow radius around the running current. */
private val NEON_CURRENT_BLUR = 4.dp

/** Neon: extra thickness of the running current over the tube. */
private val NEON_CURRENT_EXTRA_WIDTH = 1.dp

/**
 * Light mode ring brightness, as how far the vivid season color is mixed toward
 * white: at rest, and at the peak of each breath. The ring moves between the two.
 * 0 = vivid, ~0.4 = soft pastel, ~0.6 = very pale.
 * Chosen: vivid at rest, brightening toward white at the peak. (Also tried: a
 * pastel at rest turning vivid — rest 0.28–0.38 / peak 0 — too faint on device.)
 */
private const val LIGHT_RING_REST_WHITE = 0f
private const val LIGHT_RING_PEAK_WHITE = 0.3f

/** Solid: glow radius. */
private val SOLID_GLOW_BLUR = 6.dp

/** Solid: glow strength at rest, and how much it adds at the peak of a lap. */
private const val SOLID_GLOW_ALPHA = 0.6f
private const val SOLID_GLOW_ALPHA_PEAK = 0.3f

/**
 * One GPU glow layer: a [sourceWidth]-thick stroke blurred by [blur], shown at
 * [alpha]. During a lap ("breathing") the alpha rises by [alphaPeak].
 */
data class GlowLayer(
    val sourceWidth: Dp,
    val blur: Dp,
    val alpha: Float,
    val alphaPeak: Float,
    /**
     * When true the stroke starts at the ring's inner edge and extends outward
     * (like a CSS outer shadow) instead of being centered on the ring, so the
     * light spreads outside the cell rather than washing over its contents.
     */
    val outward: Boolean = false
) {
    /** How far the stroke's center line moves out from the ring's path. */
    fun inflate(ringWidth: Float, density: Density): Float =
        if (!outward) 0f else with(density) { sourceWidth.toPx() / 2 - ringWidth / 2 }

    /**
     * Blur radius in pixels. It never changes, so the caller can create the blur
     * effect once and the GPU never has to re-blur while the glow breathes.
     */
    fun blurPx(density: Density): Float = with(density) { blur.toPx().coerceAtLeast(0.5f) }

    /** Layer opacity for the current [pulse], scaled by the fade-in [level]. */
    fun alphaAt(pulse: Float, level: Float): Float = ((alpha + alphaPeak * pulse) * level).coerceIn(0f, 1f)
}

/** Light mode: a wide halo in the vivid season color and a bloom that lifts the dark ring. */
private val SOLID_GPU_GLOW = listOf(
    GlowLayer(sourceWidth = 6.dp, blur = 10.dp, alpha = 0.5f, alphaPeak = 0.4f),  // halo
    GlowLayer(sourceWidth = 4.dp, blur = 3.dp, alpha = 0.45f, alphaPeak = 0.35f) // bloom
)

/**
 * Converts a CSS-style shadow blur (as used in the design demo) into a blur
 * radius for Android: CSS blur B is a Gaussian with sigma = B / 2, and Android's
 * blur radius r maps to sigma ≈ 0.577 r, so r ≈ 0.866 B.
 */
private fun cssBlur(blur: Float): Dp = (blur * 0.866f).dp

/**
 * Dark mode ("light without an outline"): there is no sharp tube. From the center
 * out the light goes white → light season color → season color → transparent,
 * every step blurred, so no edge is ever visible. The inner two steps are drawn
 * by [SelectionRingPainter.draw]; these are the outer three, each a stroke from
 * the ring outward (like CSS outer shadows), matching the demo's (blur, spread, alpha):
 * - glow  (7, 3, 0.95) - the colored body of the light, strong and close,
 * - halo (14, 4, 0.4)  - its softer falloff,
 * - aura (28, 7, 0.16) - the faint light spilling onto the "wall".
 * (Tightened from the demo: a strong, close colored glow with a quick falloff reads
 * as clear light; a wide, uniform haze reads as milky.)
 * Breathing brightens them (the demo's brightness 1.35) rather than widening them.
 */
private val NEON_GPU_GLOW = listOf(
    GlowLayer(sourceWidth = 14.dp, blur = cssBlur(28f), alpha = 0.16f, alphaPeak = 0.06f, outward = true), // aura
    GlowLayer(sourceWidth = 8.dp, blur = cssBlur(14f), alpha = 0.4f, alphaPeak = 0.12f, outward = true),   // halo
    GlowLayer(sourceWidth = 6.dp, blur = cssBlur(7f), alpha = 0.95f, alphaPeak = 0.05f, outward = true)    // glow
)

/** Neon inner steps: (width, CSS blur, alpha) for the white center and the light-color step. */
// The white center is a thin, lightly blurred line: a wide, soft white band is
// what looked milky on device (the demo's white was a thin edge, not a band).
private val NEON_WHITE_WIDTH = 1.2.dp
private const val NEON_WHITE_BLUR = 0.7f
private const val NEON_WHITE_ALPHA = 1f
private val NEON_LIGHT_WIDTH = 3.dp
private const val NEON_LIGHT_BLUR = 2.1f
private const val NEON_LIGHT_ALPHA = 1f

/**
 * How far the "light season color" step is mixed toward white. Kept low: a
 * whitish step around the center is what makes the light look milky ("뿌연").
 */
private const val NEON_LIGHT_MIX = 0.15f

/** Neon: extra brightness of the inner steps at the peak of a lap. */
private const val NEON_INNER_PEAK = 0.15f

/** How much the glow layer grows at the peak of a lap ("breathing"). */
private const val GLOW_SWELL = 0.06f

/** Light mode: strength of the white light running around the ring. */
private const val LIGHT_LAP_ALPHA = 0.75f

/** Length of the moving light, as a fraction of the ring's circumference. */
private const val LIGHT_LENGTH = 0.12f

/** Stops used to build the moving light's sweep gradient (more = smoother band). */
private const val LIGHT_SAMPLES = 36

/** 0 at rest, 1 in the middle of a lap of the moving light. Drives the breath and the light's fade. */
fun ringPulse(lap: Float?): Float {
    val p = lap ?: return 0f
    return if (p > 0f && p < 1f) sin(p * PI).toFloat() else 0f
}

/**
 * Draws the selection ring in either [RingStyle].
 *
 * Both styles share the same motion, driven by `lap` (0..1, one lap of the
 * moving light; exactly 0 or 1 means resting):
 * - a light runs once around the ring (solid: a pale tint of the ring color;
 *   neon: a white-hot current with its own glow),
 * - meanwhile the outer glow swells and settles ("breathing").
 *
 * The outer glow is drawn by the caller into separate layers, in one of two ways:
 * - GPU ([gpuGlow], Android 12+): for each [GlowLayer] the caller draws a sharp,
 *   fairly thick stroke ([drawGlowSource]) and blurs the whole layer on the GPU.
 *   The source is thick on purpose: blurring a thin line spreads its light so far
 *   that it all but vanishes. Blur radii are fixed and breathing only changes the
 *   layers' alpha, so the blur is computed once and reused.
 * - Blur mask (older versions, or [USE_GPU_GLOW] off): [drawOuterGlow] with a fixed
 *   radius, so the blurred shape is rasterized once and reused; fading and
 *   breathing use the layer's alpha and scale ([outerGlowAlpha], [outerGlowScale]).
 *
 * Keep one instance per ring (it reuses its Paint, filters and shaders between frames).
 */
class SelectionRingPainter {
    /** True when the glow is drawn as GPU-blurred layers (see the class docs). */
    val gpuGlow = USE_GPU_GLOW && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /** The GPU glow layers for a style, drawn bottom to top. */
    fun gpuGlowLayers(style: RingStyle): List<GlowLayer> =
        if (style == RingStyle.SOLID) SOLID_GPU_GLOW else NEON_GPU_GLOW

    /** A sharp stroke in the glow color, [layer]-thick, for a GPU glow layer to blur. */
    fun drawGlowSource(
        scope: DrawScope,
        topLeft: Offset,
        size: Size,
        corner: CornerRadius,
        ringWidth: Float,
        glow: Color,
        layer: GlowLayer
    ) = with(scope) {
        val d = layer.inflate(ringWidth, this)
        drawRoundRect(
            glow,
            topLeft - Offset(d, d),
            Size(size.width + d * 2, size.height + d * 2),
            CornerRadius(corner.x + d, corner.y + d),
            style = Stroke(layer.sourceWidth.toPx())
        )
    }

    private val blurMaskSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
    private val paint = FrameworkPaint(FrameworkPaint.ANTI_ALIAS_FLAG).apply {
        style = FrameworkPaint.Style.STROKE
    }

    // Blur filters by radius (in half-pixels). A new filter object defeats the
    // platform's mask cache and forces a re-rasterize every frame.
    private val blurFilters = HashMap<Int, BlurMaskFilter>()

    // Light-band shaders, built once per color around the origin, then rotated and
    // moved to the ring with a matrix (so a moving ring doesn't need new shaders).
    private val bandShaders = HashMap<Int, SweepGradient>()
    private val bandMatrix = Matrix()

    // ── Outer glow (drawn into its own layer by the caller) ──

    /** Opacity of the glow layer. [glowLevel] (0..1) fades it in after the ring appears. */
    fun outerGlowAlpha(style: RingStyle, pulse: Float, glowLevel: Float): Float {
        // Neon layers carry their own strength (see drawOuterGlow), so the layer
        // only fades in and brightens a little during a lap.
        val a = if (style == RingStyle.SOLID) SOLID_GLOW_ALPHA + SOLID_GLOW_ALPHA_PEAK * pulse
        else 0.8f + 0.2f * pulse
        return (a * glowLevel).coerceIn(0f, 1f)
    }

    /** Scale of the glow layer around the ring's center: it swells slightly during a lap. */
    fun outerGlowScale(pulse: Float): Float = 1f + GLOW_SWELL * pulse

    /** The blurred outer glow at full strength (the layer sets its opacity). */
    fun drawOuterGlow(
        scope: DrawScope,
        topLeft: Offset,
        size: Size,
        corner: CornerRadius,
        width: Float,
        glow: Color,
        style: RingStyle
    ) = with(scope) {
        if (style == RingStyle.SOLID) {
            // Twice as thick as the ring so the glow reads on a light background.
            stroke(topLeft, size, corner, width * 2f, glow, blur = SOLID_GLOW_BLUR.toPx(), alpha = 1f)
        } else {
            // The same three outer steps as the GPU path, each with its own strength.
            NEON_GPU_GLOW.forEach { layer ->
                val d = layer.inflate(width, this)
                stroke(
                    topLeft - Offset(d, d), Size(size.width + d * 2, size.height + d * 2),
                    CornerRadius(corner.x + d, corner.y + d), layer.sourceWidth.toPx(), glow,
                    blur = layer.blur.toPx(), alpha = layer.alpha
                )
            }
        }
    }

    // ── Ring ──

    /**
     * @param lap       progress of the moving light, or null when motion is off.
     * @param glowLevel 0..1; scales the tight neon glow, so it fades in with the outer glow.
     */
    fun draw(
        scope: DrawScope,
        topLeft: Offset,
        size: Size,
        corner: CornerRadius,
        width: Float,
        colors: RingColors,
        style: RingStyle,
        lap: Float?,
        glowLevel: Float
    ): Unit = with(scope) {
        val p = lap ?: 1f
        val running = p > 0f && p < 1f
        val pulse = ringPulse(lap)
        val center = Offset(topLeft.x + size.width / 2, topLeft.y + size.height / 2)

        when (style) {
            RingStyle.SOLID -> {
                // The ring breathes too: from its resting color toward the peak color.
                // (colors.core is the resting color, still animating during the intro.)
                val peak = lerp(colors.glow, Color.White, LIGHT_RING_PEAK_WHITE)
                val ringColor = lerp(colors.core, peak, pulse)
                drawRoundRect(ringColor, topLeft, size, corner, style = Stroke(width))
                // A pale light running around the ring. The band is plain white with
                // the strength set by the paint, so its shader is built once and reused
                // (a color that changes every frame would rebuild it every frame).
                if (running) {
                    stroke(
                        topLeft, size, corner, width, Color.White, blur = 0f, alpha = pulse * LIGHT_LAP_ALPHA,
                        shader = bandShader(center, p, Color.White)
                    )
                }
            }

            RingStyle.NEON -> {
                // The inner steps of the light, both blurred so there is no edge:
                // a light season color, then the white center on top. Fixed radii,
                // so their masks stay cached.
                // The white center is lit from the start; the colored step fades in
                // together with the outer glow.
                val bright = 1f + NEON_INNER_PEAK * pulse
                val lightColor = lerp(colors.glow, Color.White, NEON_LIGHT_MIX)
                stroke(
                    topLeft, size, corner, NEON_LIGHT_WIDTH.toPx(), lightColor,
                    cssBlur(NEON_LIGHT_BLUR).toPx(), NEON_LIGHT_ALPHA * bright * glowLevel
                )
                // (Tried tinting the center with the season color while breathing; it
                // looked muddy on device, so the center stays white.)
                stroke(
                    topLeft, size, corner, NEON_WHITE_WIDTH.toPx(), colors.core,
                    cssBlur(NEON_WHITE_BLUR).toPx(), NEON_WHITE_ALPHA * bright
                )
                // A white-hot current running through the light, soft-edged as well.
                if (running) {
                    val currentWidth = width + NEON_CURRENT_EXTRA_WIDTH.toPx()
                    if (blurMaskSupported) {
                        stroke(
                            topLeft, size, corner, currentWidth, Color.White, NEON_CURRENT_BLUR.toPx(), pulse,
                            shader = bandShader(center, p, colors.glow)
                        )
                    }
                    stroke(
                        topLeft, size, corner, currentWidth, Color.White, cssBlur(NEON_WHITE_BLUR).toPx(), pulse,
                        shader = bandShader(center, p, Color.White)
                    )
                }
            }
        }
    }

    /**
     * A rounded-rect stroke, blurred when [blur] > 0.
     * With a [shader] the paint color only contributes alpha.
     */
    private fun DrawScope.stroke(
        topLeft: Offset,
        size: Size,
        corner: CornerRadius,
        width: Float,
        color: Color,
        blur: Float,
        alpha: Float,
        shader: android.graphics.Shader? = null
    ) {
        if (alpha <= 0f) return
        if (blur > 0f && !blurMaskSupported) {
            // Android 8.x fallback: three wider, fainter strokes approximate the blur.
            // A moving light (shader) is drawn unblurred instead.
            if (shader != null) return stroke(topLeft, size, corner, width, color, 0f, alpha, shader)
            for (i in 1..3) {
                drawRoundRect(
                    color = color,
                    topLeft = topLeft,
                    size = size,
                    cornerRadius = corner,
                    alpha = (alpha * 0.22f / i).coerceIn(0f, 1f),
                    style = Stroke(width + blur * i * 0.7f)
                )
            }
            return
        }
        paint.strokeWidth = width
        paint.shader = shader
        paint.color = color.copy(alpha = alpha.coerceIn(0f, 1f)).toArgb()
        paint.maskFilter = if (blur > 0f) blurFilter(blur) else null
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawRoundRect(
                topLeft.x, topLeft.y, topLeft.x + size.width, topLeft.y + size.height,
                corner.x, corner.y, paint
            )
        }
    }

    /** A cached blur filter, with the radius rounded to half a pixel. */
    private fun blurFilter(radius: Float): BlurMaskFilter {
        val key = (radius * 2).roundToInt().coerceAtLeast(1)
        return blurFilters.getOrPut(key) { BlurMaskFilter(key / 2f, BlurMaskFilter.Blur.NORMAL) }
    }

    /**
     * Sweep gradient with a soft band of [color] at [progress] (0..1 around the
     * ring, starting at 3 o'clock). Built once per color with the band at 0 around
 * the origin, then rotated and moved to [center].
     */
    private fun bandShader(center: Offset, progress: Float, color: Color): SweepGradient {
        val shader = bandShaders.getOrPut(color.toArgb()) {
            val colors = IntArray(LIGHT_SAMPLES + 1)
            val positions = FloatArray(LIGHT_SAMPLES + 1)
            for (i in 0..LIGHT_SAMPLES) {
                val t = i / LIGHT_SAMPLES.toFloat()
                // Circular distance between t and the band center (0).
                val d = minOf(t, 1f - t)
                val strength = (1f - d / LIGHT_LENGTH).coerceIn(0f, 1f)
                colors[i] = color.copy(alpha = strength * strength).toArgb()
                positions[i] = t
            }
            SweepGradient(0f, 0f, colors, positions)
        }
        bandMatrix.setRotate(progress * 360f)
        bandMatrix.postTranslate(center.x, center.y)
        shader.setLocalMatrix(bandMatrix)
        return shader
    }
}
