package io.github.sunken1110.tandemplanner.ui.effects

import android.content.Context
import android.os.Build
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

    private enum class Kind { TAP, CONFIRM, REJECT }

    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    private fun play(kind: Kind) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= 29) {
            // Predefined effects (API 29+) are referenced only inside this check.
            val effect = when (kind) {
                Kind.TAP -> VibrationEffect.EFFECT_CLICK
                Kind.CONFIRM -> VibrationEffect.EFFECT_HEAVY_CLICK
                Kind.REJECT -> VibrationEffect.EFFECT_DOUBLE_CLICK
            }
            v.vibrate(VibrationEffect.createPredefined(effect))
        } else {
            // Fallback for API 26–28: one-shot pulses approximating each effect.
            val (durationMs, amplitude) = when (kind) {
                Kind.TAP -> 12L to 120
                Kind.CONFIRM -> 25L to 255
                Kind.REJECT -> 40L to 180
            }
            v.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
        }
    }

    /** Light tick: buttons, date selection. */
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
