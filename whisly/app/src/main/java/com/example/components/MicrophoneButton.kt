package com.example.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.core.theme.WhislyDesign
import com.example.core.theme.WhislyTokens
import java.util.Locale

/**
 * Portal Voice Recognition Orb & Record Button Component for Jetpack Compose (Phase 2).
 *
 * Implements the glowing portal of warp-speed light, coupled with self-contained RECORD_AUDIO
 * runtime permission handling and real-time microphone audio level reactivity.
 */
@Composable
fun HeroMicrophoneControl(
    isRecording: Boolean,
    recordingElapsedMs: Long,
    amplitudes: List<Float>,
    statusHint: String,
    sourceLanguageName: String,
    targetLanguageName: String,
    onToggleRecording: () -> Unit,
    onPermissionDenied: (() -> Unit)? = null,
    lowEndOptimization: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = WhislyDesign.colors

    // Real-time smoothed audio level [0f..1f] derived from live AudioRecord amplitudes
    val rawAudioLevel = remember(amplitudes, isRecording) {
        if (!isRecording || amplitudes.isEmpty()) {
            0f
        } else {
            val recent = amplitudes.takeLast(6)
            recent.average().toFloat().coerceIn(0.0f, 1.0f)
        }
    }

    // Self-contained RECORD_AUDIO runtime permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onToggleRecording()
        } else {
            onPermissionDenied?.invoke()
        }
    }

    val orbState = when {
        isRecording -> AuraOrbState.LISTENING
        statusHint.contains("Transcribing", ignoreCase = true) || statusHint.contains("Translating", ignoreCase = true) -> AuraOrbState.PROCESSING
        statusHint.contains("ready", ignoreCase = true) -> AuraOrbState.SUCCESS
        statusHint.contains("pack", ignoreCase = true) || statusHint.contains("download", ignoreCase = true) -> AuraOrbState.DISABLED
        statusHint.contains("Error", ignoreCase = true) || statusHint.contains("Try again", ignoreCase = true) -> AuraOrbState.ERROR
        else -> AuraOrbState.IDLE
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth()
    ) {
        // Aura Voice Orb (Phase 4: 88dp core, peach radial glow, concentric hairline rings, 168dp touch container)
        AuraVoiceOrb(
            state = orbState,
            micLevel = rawAudioLevel,
            onTap = {
                val hasPerm = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (isRecording || hasPerm) {
                    onToggleRecording()
                } else {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            lowEndMode = lowEndOptimization,
            size = 168.dp,
            modifier = Modifier.size(168.dp)
        )

        Spacer(modifier = Modifier.height(WhislyTokens.Space12))

        // Real audio level waveform strip
        AudioWaveformView(
            amplitudes = amplitudes,
            isRecording = isRecording,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 42.dp)
                .height(28.dp)
        )

        Spacer(modifier = Modifier.height(WhislyTokens.Space8))

        if (isRecording) {
            val totalSeconds = (recordingElapsedMs / 1000L).toInt()
            val mins = totalSeconds / 60
            val secs = totalSeconds % 60
            val tenths = ((recordingElapsedMs % 1000L) / 100L).toInt()
            Surface(
                shape = RoundedCornerShape(WhislyTokens.RadiusControl),
                color = colors.cardSurface,
                border = BorderStroke(1.dp, WhislyTokens.PrimaryOrange.copy(alpha = 0.5f))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(WhislyTokens.PrimaryOrange)
                    )
                    Text(
                        text = String.format(Locale.US, "%02d:%02d.%d", mins, secs, tenths),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = colors.primaryCocoa
                        )
                    )
                }
            }
        } else if (statusHint.isNotBlank()) {
            Text(
                text = statusHint,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = colors.mutedText,
                    fontWeight = FontWeight.Medium
                ),
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }
    }
}

@Composable
fun AudioWaveformView(
    amplitudes: List<Float>,
    isRecording: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = WhislyDesign.colors
    Canvas(modifier = modifier.testTag("audio_waveform_canvas")) {
        val barCount = amplitudes.size.coerceAtLeast(1)
        val totalWidth = size.width
        val maxBarHeight = size.height
        val spacing = totalWidth / (barCount * 2.1f)
        val barWidth = spacing * 1.1f
        val flatHeightPx = 3.dp.toPx()

        for (i in 0 until barCount) {
            val rawAmp = if (isRecording) amplitudes[i].coerceIn(0f, 1f) else 0f
            val isVoicedBar = isRecording && rawAmp > 0.01f
            val barHeight = if (isVoicedBar) {
                (maxBarHeight * rawAmp).coerceAtLeast(flatHeightPx)
            } else {
                flatHeightPx
            }
            val x = i * (barWidth + spacing)
            val y = (maxBarHeight - barHeight) / 2f
            val color = if (isVoicedBar) {
                if (i % 2 == 0) colors.primaryPeach else WhislyTokens.PrimaryOrange
            } else {
                colors.secondaryPeach.copy(alpha = 0.42f)
            }
            drawRoundRect(
                color = color,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
