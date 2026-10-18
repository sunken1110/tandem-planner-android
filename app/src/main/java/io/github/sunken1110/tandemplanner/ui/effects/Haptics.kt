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

    /** Light tick: buttons, date selection. */
    fun tap() = play(VibrationEffect.EFFECT_CLICK, 12, 120)

    /** Firm click: successful save. */
    fun confirm() = play(VibrationEffect.EFFECT_HEAVY_CLICK, 25, 255)

    /** Double tick: delete, or invalid input. */
    fun reject() = play(VibrationEffect.EFFECT_DOUBLE_CLICK, 40, 180)
}

@Composable
fun rememberHaptics(): Haptics {
    val context = LocalContext.current
    return remember(context) { Haptics(context.applicationContext) }
}
