package com.originsu.manager.ui.component.splash

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.originsu.manager.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.time.Duration.Companion.milliseconds

private const val EXIT_DURATION_MS = 360


@Composable
fun SplashScreen(
    isReady: Boolean,
    minDurationMs: Long = 850L,
    onDismissed: () -> Unit = {},
) {
    var contentVisible by remember { mutableStateOf(false) }
    var exitTriggered by remember { mutableStateOf(false) }
    var splashDismissed by remember { mutableStateOf(false) }
    val currentOnDismissed by rememberUpdatedState(onDismissed)

    val logoScale = remember { Animatable(0.72f) }
    val logoAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        contentVisible = true
        launch { logoAlpha.animateTo(1f, tween(320, easing = FastOutSlowInEasing)) }
        launch {
            logoScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow,
                ),
            )
        }
    }

    LaunchedEffect(isReady) {
        if (isReady) {
            delay(minDurationMs.milliseconds)
            exitTriggered = true
            delay((EXIT_DURATION_MS.toLong() + 20L).milliseconds)
            splashDismissed = true
            currentOnDismissed()
        }
    }

    if (splashDismissed) return

    AnimatedVisibility(
        visible = !exitTriggered,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(EXIT_DURATION_MS, easing = FastOutSlowInEasing)) +
            scaleOut(
                targetScale = 1.05f,
                animationSpec = tween(EXIT_DURATION_MS, easing = FastOutSlowInEasing),
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MiuixTheme.colorScheme.background)
                .pointerInput(Unit) {}
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // Top placeholder to balance the column layout
                Spacer(modifier = Modifier.height(16.dp))

                // Center Branding
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    // Signature leaf only: no plate or shadow behind it, tinted
                    // against the splash background so it is always legible.
                    Image(
                        painter = painterResource(R.drawable.ic_launcher_monochrome),
                        contentDescription = stringResource(id = R.string.app_name),
                        modifier = Modifier
                            .graphicsLayer {
                                scaleX = logoScale.value
                                scaleY = logoScale.value
                                alpha = logoAlpha.value
                            }
                            .size(104.dp),
                        colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onBackground),
                        contentScale = ContentScale.Fit,
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    AnimatedVisibility(
                        visible = contentVisible,
                        enter = fadeIn(tween(400, delayMillis = 180, easing = FastOutSlowInEasing)),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(id = R.string.app_name),
                                color = MiuixTheme.colorScheme.onBackground,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.3).sp,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Kernel-based Root Solution",
                                color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }

                // Bottom Footer
                AnimatedVisibility(
                    visible = contentVisible,
                    enter = fadeIn(tween(450, delayMillis = 260, easing = FastOutSlowInEasing)),
                ) {
                    Text(
                        text = "What you like, you permit; what you permit, you govern.",
                        color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.35f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}
