package com.jarvis.ai.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jarvis.ai.ui.theme.ArcGlowAmber
import com.jarvis.ai.ui.theme.ElectricViolet
import com.jarvis.ai.ui.theme.JarvisBlue
import com.jarvis.ai.ui.theme.JarvisCyan
import com.jarvis.ai.ui.theme.NeonLime

enum class OrbState {
    IDLE,
    LISTENING,
    THINKING,
    EXECUTING
}

@Composable
fun ArcReactorOrb(
    modifier: Modifier = Modifier,
    size: Dp = 160.dp,
    state: OrbState = OrbState.IDLE,
    audioLevel: Float = 0f // 0f to 1f normalized audio amplitude
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ArcReactorAnimations")

    // Slow continuous rotation for outer mechanical ring
    val outerRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "outerRingRotation"
    )

    // Reverse fast rotation for inner telemetry ring
    val innerRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "innerRingRotation"
    )

    // Breathing pulse for center reactor core
    val corePulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "coreBreathing"
    )

    // Dynamic state-driven core colors
    val primaryGlow by animateColorAsState(
        targetValue = when (state) {
            OrbState.IDLE -> JarvisCyan
            OrbState.LISTENING -> JarvisBlue
            OrbState.THINKING -> ArcGlowAmber
            OrbState.EXECUTING -> NeonLime
        },
        animationSpec = tween(600),
        label = "primaryGlowColor"
    )

    val secondaryGlow by animateColorAsState(
        targetValue = when (state) {
            OrbState.IDLE -> JarvisBlue
            OrbState.LISTENING -> ElectricViolet
            OrbState.THINKING -> Color(0xFFFF6D00)
            OrbState.EXECUTING -> JarvisCyan
        },
        animationSpec = tween(600),
        label = "secondaryGlowColor"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val baseRadius = this.size.minDimension / 2.2f

            // 1. Ambient Background Glow Aura
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryGlow.copy(alpha = 0.35f + (audioLevel * 0.3f)),
                        secondaryGlow.copy(alpha = 0.15f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = baseRadius * 1.3f
                ),
                radius = baseRadius * 1.3f,
                center = center
            )

            // 2. Outer Segmented Tech Ring
            rotate(degrees = outerRotation, pivot = center) {
                drawCircle(
                    color = primaryGlow.copy(alpha = 0.4f),
                    radius = baseRadius * 0.95f,
                    center = center,
                    style = Stroke(
                        width = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(30f, 20f, 10f, 20f), 0f)
                    )
                )
            }

            // 3. Middle Counter-Rotating Telemetry Ring
            rotate(degrees = innerRotation, pivot = center) {
                drawCircle(
                    color = secondaryGlow.copy(alpha = 0.6f),
                    radius = baseRadius * 0.75f,
                    center = center,
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
                    )
                )
            }

            // 4. Reactive Audio Wave Ring
            val dynamicAudioRadius = baseRadius * (0.55f + (audioLevel * 0.25f))
            drawCircle(
                color = primaryGlow.copy(alpha = 0.8f),
                radius = dynamicAudioRadius,
                center = center,
                style = Stroke(width = 3.dp.toPx())
            )

            // 5. Central Reactor Core (High Intensity Glow)
            val currentCoreRadius = (baseRadius * 0.38f) * corePulse
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White,
                        primaryGlow,
                        secondaryGlow,
                        Color.Transparent
                    ),
                    center = center,
                    radius = currentCoreRadius
                ),
                radius = currentCoreRadius,
                center = center
            )
        }
    }
}
