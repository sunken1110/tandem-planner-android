package io.github.sunken1110.tandemplanner.ui.effects

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Short haptic feedback.
 *
 * Drives the vibrator directly instead of View.performHapticFeedback(), which
 * is silently ignored when the system "touch vibration" setting is off or the
 * OEM implementation is weak.
 *
 * Requires `<uses-permission android:name="android.permission.VIBRATE" />`.
 */
class Haptics(context: Context) {

    private enum class Kind { TICK, TAP, CONFIRM, REJECT }

    private val vibrator: Vibrator? =
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator

    private fun play(kind: Kind) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val effect = when (kind) {
            Kind.TICK -> VibrationEffect.EFFECT_TICK
            Kind.TAP -> VibrationEffect.EFFECT_CLICK
            Kind.CONFIRM -> VibrationEffect.EFFECT_HEAVY_CLICK
            Kind.REJECT -> VibrationEffect.EFFECT_DOUBLE_CLICK
        }
        v.vibrate(VibrationEffect.createPredefined(effect))
    }

    /** Very light tick: each step of a scrolling wheel. */
    fun tick() = play(Kind.TICK)

    /** Light click: buttons, date selection. */
    fun tap() = play(Kind.TAP)

    /** Firm click: successful save. */
    fun confirm() = play(Kind.CONFIRM)

    /** Double tick: delete, or invalid input. */
    fun reject() = play(Kind.REJECT)
}

@Composable
fun rememberHaptics(): Haptics {
    val context = LocalContext.current
    return remember(context) { Haptics(context.applicationContext) }
}
