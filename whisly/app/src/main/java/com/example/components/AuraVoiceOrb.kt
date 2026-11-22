package com.example.components

import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.core.debug.StartupSwitchboard
import com.example.core.theme.WhislyDesign
import com.example.core.theme.WhislyTheme
import com.example.core.theme.WhislyTokens
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * Aura Voice Orb States:
 * - IDLE: Calm, premium breathing glow and concentric hairline rings (~3.2s period)
 * - PRESS: Soft scale-down to 0.96 with haptic tick
 * - LISTENING: Core pulses with real mic RMS level; 48-point circular reactive waveform; voice peak ripples
 * - PROCESSING: Waveform smooths into a thin gold arc rotating around the core (~1.2s per turn)
 * - SUCCESS: Single ring expands and fades (~400ms); checkmark confirmation
 * - ERROR: Error rim soft pulse (~500ms) with inline error guidance
 * - DISABLED: Desaturated core with download icon (NeedsPack)
 */
enum class AuraOrbState(val code: Float, val description: String) {
    IDLE(0f, "Tap to speak"),
    LISTENING(1f, "Listening, tap to finish"),
    PROCESSING(2f, "Processing speech"),
    SUCCESS(3f, "Translation ready"),
    ERROR(4f, "Error occurred"),
    DISABLED(5f, "Download language pack to speak")
}

// Backward compatibility alias for any existing references
typealias PortalOrbState = AuraOrbState

/**
 * Native Jetpack Compose "Aura" Voice Orb (Phase 4).
 *
 * Implements:
 * - Core: 88dp primary-orange (#F45A18) disc with white mic icon and subtle top-light highlight
 * - Radial glow: Peach (#EBAF96) fading to transparent
 * - Concentric hairline rings: 2 thin peach rings with gentle alpha
 * - Gold (#FFAB32) accent: used during processing (rotating arc)
 * - Zero allocations in Canvas draw loops: pre-computed trigonometry tables
 * - 0% CPU at idle when screen is off; idle breathing capped at <=30 fps
 * - Full reduced-motion and ANIMATOR_DURATION_SCALE support (crisp static display)
 * - 168dp touch container with debounced interaction and tactile haptics
 */
@Composable
fun AuraVoiceOrb(
    state: AuraOrbState,
    micLevel: Float,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 168.dp,
    lowEndMode: Boolean = false,
    enabled: Boolean = true
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val colors = WhislyDesign.colors

    val reducedMotion = remember {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        }.getOrDefault(false)
    }

    val pressScale = remember { Animatable(1f) }
    val successExpand = remember { Animatable(0f) }
    val errorPulse = remember { Animatable(0f) }

    var smoothedLevel by remember { mutableFloatStateOf(0f) }
    var idleTimeSeconds by remember { mutableFloatStateOf(0f) }
    var processingAngle by remember { mutableFloatStateOf(0f) }
    var lastTapTimeMs by remember { mutableLongStateOf(0L) }

    // Pre-allocated trigonometry table for circular waveform points (48 points standard, 24 in low-end mode)
    val pointCount = if (lowEndMode) 24 else 48
    val cosTable = remember(pointCount) {
        FloatArray(pointCount) { i -> cos(i * 2.0 * PI / pointCount).toFloat() }
    }
    val sinTable = remember(pointCount) {
        FloatArray(pointCount) { i -> sin(i * 2.0 * PI / pointCount).toFloat() }
    }

    // Pre-allocated particle cloud (20-40 particles). Drawn as static decoration at idle and
    // audio-reactive only while recording, so idle never animates.
    val particleCount = if (lowEndMode) 20 else 40
    val particleCos = remember(particleCount) {
        FloatArray(particleCount) { i ->
            cos((i * 2.37f) + (i % 7) * 0.41f).toFloat()
        }
    }
    val particleSin = remember(particleCount) {
        FloatArray(particleCount) { i ->
            sin((i * 2.37f) + (i % 7) * 0.41f).toFloat()
        }
    }
    val particleRadius = remember(particleCount) {
        FloatArray(particleCount) { i -> 1.05f + (i % 5) * 0.07f }
    }

    // Success animation trigger
    LaunchedEffect(state) {
        if (state == AuraOrbState.SUCCESS) {
            successExpand.snapTo(0f)
            successExpand.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
            )
        } else if (state == AuraOrbState.ERROR) {
            errorPulse.snapTo(0f)
            errorPulse.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
            )
        }
    }

    // Unified animation clock:
    // When IDLE: ticks at ~30 fps for gentle breathing.
    // When LISTENING / PROCESSING: ticks at full frame rate.
    // When reduced motion: completely stopped.
    val isClockDisabled = StartupSwitchboard.isClockDisabled(context)
    val shouldRunClock = !reducedMotion && !isClockDisabled && enabled && (
        state == AuraOrbState.LISTENING || state == AuraOrbState.PROCESSING
    )
    val latestMicLevel by rememberUpdatedState(micLevel)

    LaunchedEffect(shouldRunClock, state) {
        if (!shouldRunClock) return@LaunchedEffect

        var lastFrameMs = 0L
        while (shouldRunClock) {
            withInfiniteAnimationFrameMillis { nowMs ->
                val dt = if (lastFrameMs == 0L) 0.016f else ((nowMs - lastFrameMs) / 1000f).coerceIn(0.001f, 0.06f)
                lastFrameMs = nowMs

                // 1. Idle breathing clock (3.2 second cycle)
                idleTimeSeconds = (idleTimeSeconds + dt) % 3.2f

                // 2. Microphone RMS smoothing: fast attack (80ms), slow release (220ms)
                val target = latestMicLevel.coerceIn(0f, 1f)
                val rate = if (target > smoothedLevel) 0.08f else 0.22f
                smoothedLevel += (target - smoothedLevel) * (1f - exp(-dt / rate))

                // 3. Processing rotation clock (1.2s per 360-degree rotation)
                if (state == AuraOrbState.PROCESSING) {
                    processingAngle = (processingAngle + (360f / 1.2f) * dt) % 360f
                }
            }
        }
    }

    // Breathing calculations: static at idle (no continuous animation); reactive only while recording.
    val breathCycle = 0f
    val idleGlowScale = 1.00f
    val idleGlowAlpha = 0.25f

    // Core scale: 0.94 during processing, reactive pulse during listening
    val corePulseScale = when (state) {
        AuraOrbState.LISTENING -> 1.0f + smoothedLevel * 0.12f
        AuraOrbState.PROCESSING -> 0.94f
        else -> 1.0f
    } * pressScale.value

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = pressScale.value
                scaleY = pressScale.value
            }
            .clip(CircleShape)
            .semantics {
                role = Role.Button
                contentDescription = state.description
            }
            .pointerInput(state, enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        scope.launch {
                            pressScale.animateTo(0.96f, tween(120, easing = FastOutSlowInEasing))
                        }
                        val released = tryAwaitRelease()
                        scope.launch {
                            pressScale.animateTo(1f, tween(160, easing = FastOutSlowInEasing))
                        }
                    },
                    onTap = {
                        val now = System.currentTimeMillis()
                        if (now - lastTapTimeMs > 300L) {
                            lastTapTimeMs = now
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onTap()
                        }
                    }
                )
            }
            .testTag("hero_microphone_button")
    ) {
        // Background Glow, Concentric Rings & Circular Waveform Canvas
        Box(modifier = Modifier.fillMaxSize().testTag("strands_voice_orb_canvas")) {
            Canvas(modifier = Modifier.fillMaxSize().testTag("aura_orb_canvas")) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val coreRadius = 44.dp.toPx() // 88dp core diameter

            // 1. Soft radial peach glow
            val glowColor = when (state) {
                AuraOrbState.ERROR -> WhislyTokens.Error.copy(alpha = 0.25f * (1f - errorPulse.value))
                AuraOrbState.SUCCESS -> WhislyTokens.GoldenAccent.copy(alpha = 0.35f * (1f - successExpand.value))
                AuraOrbState.DISABLED -> Color.Transparent
                else -> WhislyTokens.SecondaryPeach.copy(
                    alpha = if (state == AuraOrbState.LISTENING) (0.20f + smoothedLevel * 0.40f) else idleGlowAlpha
                )
            }
            val glowRadius = coreRadius * (if (state == AuraOrbState.LISTENING) (1.4f + smoothedLevel * 0.35f) else (1.35f * idleGlowScale))
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(glowColor, Color.Transparent),
                    center = center,
                    radius = glowRadius
                ),
                radius = glowRadius,
                center = center
            )

            // 2. Two thin concentric hairline rings (peach with gentle alpha)
            val ringAlpha = if (state == AuraOrbState.DISABLED) 0.10f else (0.35f - breathCycle * 0.15f)
            val ringColor = WhislyTokens.SecondaryPeach.copy(alpha = ringAlpha)
            val ring1Radius = coreRadius * (1.20f * (if (reducedMotion) 1f else idleGlowScale))
            val ring2Radius = coreRadius * (1.45f * (if (reducedMotion) 1f else idleGlowScale))

            drawCircle(
                color = ringColor,
                radius = ring1Radius,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )
            drawCircle(
                color = ringColor.copy(alpha = ringAlpha * 0.65f),
                radius = ring2Radius,
                center = center,
                style = Stroke(width = 0.75.dp.toPx())
            )

            // 2.5. Particle cloud (20-40 points). Static at idle; audio-reactive only while recording.
            val particleBaseRadius = coreRadius * 1.18f
            val particleDrift = if (state == AuraOrbState.LISTENING) smoothedLevel else 0f
            for (i in 0 until particleCount) {
                val r = particleBaseRadius * particleRadius[i] + particleDrift * 6.dp.toPx()
                val x = center.x + particleCos[i] * r
                val y = center.y + particleSin[i] * r
                val alpha = (0.12f + particleDrift * 0.35f).coerceIn(0.08f, 0.55f)
                val dotSize = (1.2f + particleDrift * 1.4f).dp.toPx()
                drawCircle(
                    color = WhislyTokens.SecondaryPeach.copy(alpha = alpha),
                    radius = dotSize,
                    center = Offset(x, y)
                )
            }

            // 3. Listening State: Circular Waveform (48 reactive points/bars around the core)
            if (state == AuraOrbState.LISTENING) {
                val waveBaseRadius = coreRadius + 4.dp.toPx()
                val waveMaxDelta = 22.dp.toPx()
                val barColor = WhislyTokens.PrimaryOrange.copy(alpha = 0.85f)
                val peakColor = WhislyTokens.GoldenAccent

                for (i in 0 until pointCount) {
                    val angleCos = cosTable[i]
                    val angleSin = sinTable[i]
                    // Sub-band frequency perturbation derived from audio level and angular position
                    val perturbation = (sin((i * 3.5f) + idleTimeSeconds * 6f) * 0.5f + 0.5f)
                    val barLen = (smoothedLevel * waveMaxDelta * (0.4f + 0.6f * perturbation)).coerceIn(1.5.dp.toPx(), waveMaxDelta)

                    val startX = center.x + angleCos * waveBaseRadius
                    val startY = center.y + angleSin * waveBaseRadius
                    val endX = center.x + angleCos * (waveBaseRadius + barLen)
                    val endY = center.y + angleSin * (waveBaseRadius + barLen)

                    drawLine(
                        color = if (smoothedLevel > 0.65f) peakColor else barColor,
                        start = Offset(startX, startY),
                        end = Offset(endX, endY),
                        strokeWidth = if (lowEndMode) 2.5.dp.toPx() else 1.75.dp.toPx()
                    )
                }

                // Voice peak ripples on strong microphone bursts
                if (smoothedLevel > 0.45f && !reducedMotion) {
                    val peakRippleRadius = coreRadius + 6.dp.toPx() + smoothedLevel * 18.dp.toPx()
                    drawCircle(
                        color = WhislyTokens.PrimaryOrange.copy(alpha = (smoothedLevel - 0.45f) * 0.5f),
                        radius = peakRippleRadius,
                        center = center,
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
            }

            // 4. Processing State: Thin gold rotating arc around the core (1.2s per turn)
            if (state == AuraOrbState.PROCESSING) {
                val arcRadius = coreRadius + 6.dp.toPx()
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(Color.Transparent, WhislyTokens.GoldenAccent, Color.Transparent),
                        center = center
                    ),
                    startAngle = processingAngle,
                    sweepAngle = 130f,
                    useCenter = false,
                    topLeft = Offset(center.x - arcRadius, center.y - arcRadius),
                    size = androidx.compose.ui.geometry.Size(arcRadius * 2f, arcRadius * 2f),
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            // 5. Success State: Expanding single ring
            if (state == AuraOrbState.SUCCESS && successExpand.value > 0f) {
                val expRadius = coreRadius + successExpand.value * 28.dp.toPx()
                val expAlpha = (1f - successExpand.value).coerceIn(0f, 1f)
                drawCircle(
                    color = WhislyTokens.GoldenAccent.copy(alpha = expAlpha),
                    radius = expRadius,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            // 6. Error State: Error rim pulse
            if (state == AuraOrbState.ERROR) {
                drawCircle(
                    color = WhislyTokens.Error.copy(alpha = (1f - errorPulse.value) * 0.8f),
                    radius = coreRadius + 2.dp.toPx(),
                    center = center,
                    style = Stroke(width = 2.5.dp.toPx())
                )
            }
        }
        }

        // 88dp Primary Core Disc with Top-Light Highlight and Icon
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(88.dp)
                .graphicsLayer {
                    scaleX = corePulseScale
                    scaleY = corePulseScale
                }
                .clip(CircleShape)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val coreRadius = this.size.minDimension / 2f
                val center = Offset(this.size.width / 2f, this.size.height / 2f)

                val coreBg = when (state) {
                    AuraOrbState.DISABLED -> Color(0xFF8A7D77) // Desaturated muted cocoa
                    AuraOrbState.ERROR -> Color(0xFFC0392B)
                    else -> WhislyTokens.PrimaryOrange
                }

                // Base solid disc
                drawCircle(
                    color = coreBg,
                    radius = coreRadius,
                    center = center
                )

                // Subtle top-light gradient highlight (gentle directional light from 12 o'clock)
                drawCircle(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.28f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.12f)
                        ),
                        startY = 0f,
                        endY = this.size.height
                    ),
                    radius = coreRadius,
                    center = center
                )

                // Inner hairline containment rim
                drawCircle(
                    color = Color.White.copy(alpha = 0.20f),
                    radius = coreRadius - 0.75.dp.toPx(),
                    center = center,
                    style = Stroke(width = 1.dp.toPx())
                )
            }

            // Central Icon: Mic / Stop / Check / Download
            val iconTint = Color.White
            when (state) {
                AuraOrbState.SUCCESS -> {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Success",
                        tint = iconTint,
                        modifier = Modifier.size(36.dp)
                    )
                }
                AuraOrbState.LISTENING -> {
                    // Recording state: mic with recording dot or stop square
                    Icon(
                        imageVector = Icons.Filled.Stop,
                        contentDescription = "Stop recording",
                        tint = iconTint,
                        modifier = Modifier.size(32.dp)
                    )
                }
                AuraOrbState.DISABLED -> {
                    Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = "Download pack",
                        tint = iconTint,
                        modifier = Modifier.size(32.dp)
                    )
                }
                else -> {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = "Microphone",
                        tint = iconTint,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }
    }
}

// ==================== Backward Compatibility & Migration Wrappers ====================

/**
 * Compatibility wrapper routing [PortalVoiceOrb] directly to the new [AuraVoiceOrb].
 * Preserves existing signatures in [MicrophoneButton.kt] and [HomeScreen.kt].
 */
@Composable
fun PortalVoiceOrb(
    state: AuraOrbState,
    micLevel: Float,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 168.dp,
    lowEndMode: Boolean = false
) {
    AuraVoiceOrb(
        state = state,
        micLevel = micLevel,
        onTap = onTap,
        modifier = modifier,
        size = size,
        lowEndMode = lowEndMode
    )
}

// ==================== Compose Previews ====================

@Preview(name = "Aura Voice Orb - Idle Light", showBackground = true, backgroundColor = 0xFFFFF9F5)
@Composable
private fun PreviewAuraOrbIdleLight() {
    WhislyTheme(darkTheme = false) {
        Box(modifier = Modifier.size(200.dp), contentAlignment = Alignment.Center) {
            AuraVoiceOrb(
                state = AuraOrbState.IDLE,
                micLevel = 0f,
                onTap = {}
            )
        }
    }
}

@Preview(name = "Aura Voice Orb - Listening Light", showBackground = true, backgroundColor = 0xFFFFF9F5)
@Composable
private fun PreviewAuraOrbListeningLight() {
    WhislyTheme(darkTheme = false) {
        Box(modifier = Modifier.size(200.dp), contentAlignment = Alignment.Center) {
            AuraVoiceOrb(
                state = AuraOrbState.LISTENING,
                micLevel = 0.65f,
                onTap = {}
            )
        }
    }
}

@Preview(name = "Aura Voice Orb - Processing Dark", showBackground = true, backgroundColor = 0xFF1A1412)
@Composable
private fun PreviewAuraOrbProcessingDark() {
    WhislyTheme(darkTheme = true) {
        Box(modifier = Modifier.size(200.dp), contentAlignment = Alignment.Center) {
            AuraVoiceOrb(
                state = AuraOrbState.PROCESSING,
                micLevel = 0f,
                onTap = {}
            )
        }
    }
}
