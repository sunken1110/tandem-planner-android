package io.github.sunken1110.tandemplanner.ui.effects

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import io.github.sunken1110.tandemplanner.INTRO_HOLD_MS
import io.github.sunken1110.tandemplanner.INTRO_IN_MS
import io.github.sunken1110.tandemplanner.INTRO_OUT_MS
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * Full-screen intro shown right after the system splash.
 *
 * Starts on the same backdrop color as the splash so the hand-off is seamless,
 * fades the image in with a slow zoom-out (Ken Burns style), holds briefly,
 * then calls [onFinished]. Tapping anywhere skips it.
 */
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
        // The zoom continues through fade-in, hold and fade-out.
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
