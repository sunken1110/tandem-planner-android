package io.github.sunken1110.tandemplanner

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import io.github.sunken1110.tandemplanner.data.SharedCalendar
import io.github.sunken1110.tandemplanner.ui.calendar.CalendarScreen
import io.github.sunken1110.tandemplanner.ui.effects.IntroOverlay
import io.github.sunken1110.tandemplanner.ui.setup.SetupScreen
import io.github.sunken1110.tandemplanner.ui.theme.TandemPlannerTheme

/**
 * Single entry point of the app.
 *
 * Flow: system splash → optional intro image → setup screen (if not yet in a
 * shared calendar) or the main calendar screen.
 */
class MainActivity : ComponentActivity() {

    @SuppressLint("DiscouragedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        // Must be called before super.onCreate().
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // Fade the system splash out instead of cutting it off abruptly.
        splashScreen.setOnExitAnimationListener { provider ->
            provider.view.animate()
                .alpha(0f)
                .setDuration(SPLASH_FADE_MS)
                .withEndAction { provider.remove() }
                .start()
        }

        enableEdgeToEdge()

        // Optional resources looked up by name so the app still runs without them.
        // - res/drawable-nodpi/splash_bg.*  → full-screen intro image
        // - res/values/colors.xml splash_background → shared splash/intro backdrop color
        val introImage = resources.getIdentifier("splash_bg", "drawable", packageName)
        val splashColorId = resources.getIdentifier("splash_background", "color", packageName)
        val splashColor = if (splashColorId != 0) Color(resources.getColor(splashColorId, theme)) else null

        setContent {
            // Dynamic (wallpaper-based) color is off so seasonal colors stay consistent.
            TandemPlannerTheme(dynamicColor = false) {
                var showIntro by rememberSaveable { mutableStateOf(introImage != 0) }

                // Main content fades in while the intro fades out (cross-fade).
                val mainAlpha by animateFloatAsState(
                    targetValue = if (showIntro) 0f else 1f,
                    animationSpec = tween(INTRO_OUT_MS),
                    label = "mainAlpha"
                )

                // One shared-calendar repository for the whole app.
                val calendar = remember { SharedCalendar(applicationContext) }

                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize().graphicsLayer { alpha = mainAlpha }) {
                            Crossfade(
                                targetState = calendar.roomCode != null,
                                animationSpec = tween(500),
                                label = "setupOrCalendar"
                            ) { joined ->
                                if (joined) {
                                    // The year fill animation waits until the intro is gone.
                                    CalendarScreen(calendar = calendar, startFill = !showIntro)
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
