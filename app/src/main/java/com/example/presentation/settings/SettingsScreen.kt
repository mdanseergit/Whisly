package com.example.presentation.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.OfflineBolt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.ai.evaluation.AiEvaluationSuite
import com.example.ai.pipeline.AudioDiagnostics
import com.example.core.theme.WhislyDesign
import com.example.core.theme.WhislyTheme
import com.example.core.theme.WhislyTokens
import com.example.data.local.AppSettingsEntity
import java.util.Locale

/**
 * PHASE 4 & PHASE 8 — Clean User Settings & Privacy Screen (`SettingsScreen.kt`).
 *
 * - iOS-style grouped lists (rounded group `20dp`, leading icon, Bold/SemiBold title, trailing control)
 * - No redundant multi-line descriptions under toggles
 * - All developer debug panels, telemetry dumps, and error state simulators removed from `src/main`
 */
@Composable
fun SettingsScreen(
    settings: AppSettingsEntity?,
    telemetry: AiEvaluationSuite.RuntimeTelemetrySnapshot,
    audioDiagnostics: AudioDiagnostics = AudioDiagnostics(),
    usedModelStorageBytes: Long,
    onToggleOfflineOnly: (Boolean) -> Unit,
    onSpeechSpeedChange: (Float) -> Unit,
    onToggleAutoPlay: (Boolean) -> Unit,
    onToggleWifiOnly: (Boolean) -> Unit,
    onToggleCorrectionConsent: (Boolean) -> Unit,
    onClearHistory: () -> Unit,
    onOpenAboutWhisly: () -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
    val colors = WhislyDesign.colors
    val currentSettings = settings ?: AppSettingsEntity()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = WhislyTokens.Space24)
            .testTag("settings_screen")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = WhislyTokens.Space12, vertical = WhislyTokens.Space8)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("settings_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = colors.primaryCocoa
                )
            }
            Text(
                text = "Settings & Privacy",
                style = MaterialTheme.typography.titleLarge.copy(
                    color = colors.primaryCocoa,
                    fontWeight = FontWeight.Bold
                )
            )
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(WhislyTokens.Space16),
            modifier = Modifier.padding(horizontal = WhislyTokens.ScreenHorizontalPadding)
        ) {
            // 1. Privacy & Downloads Group
            SettingsSectionCard(title = "Privacy & Downloads") {
                SettingsToggleRow(
                    icon = Icons.Outlined.OfflineBolt,
                    title = "Offline-Only Mode",
                    checked = currentSettings.offlineOnlyMode,
                    onCheckedChange = onToggleOfflineOnly,
                    testTag = "toggle_offline_only"
                )
                HorizontalDivider(color = colors.border)
                SettingsToggleRow(
                    icon = Icons.Outlined.Wifi,
                    title = "Download Over Wi-Fi Only",
                    checked = currentSettings.downloadOverWifiOnly,
                    onCheckedChange = onToggleWifiOnly,
                    testTag = "toggle_wifi_only"
                )
                HorizontalDivider(color = colors.border)
                SettingsToggleRow(
                    icon = Icons.Outlined.Share,
                    title = "Share Edited Transcripts",
                    checked = currentSettings.consentToCorrectionSync,
                    onCheckedChange = onToggleCorrectionConsent,
                    testTag = "toggle_correction_consent"
                )
            }

            // 2. Voice Playback Group
            SettingsSectionCard(title = "Voice Playback") {
                SettingsToggleRow(
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    title = "Automatic Voice Playback",
                    checked = currentSettings.autoPlayTranslatedSpeech,
                    onCheckedChange = onToggleAutoPlay,
                    testTag = "toggle_autoplay_speech"
                )
                HorizontalDivider(color = colors.border)
                Column(modifier = Modifier.padding(vertical = WhislyTokens.Space8)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Speed,
                                contentDescription = null,
                                tint = WhislyTokens.PrimaryOrange,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Playback Speed",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    color = colors.primaryCocoa,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                        Text(
                            text = String.format(Locale.US, "%.2fx", currentSettings.speechSpeed),
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = colors.primaryAccent,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    Slider(
                        value = currentSettings.speechSpeed,
                        onValueChange = onSpeechSpeedChange,
                        valueRange = 0.7f..1.4f,
                        colors = SliderDefaults.colors(
                            thumbColor = colors.primaryAccent,
                            activeTrackColor = colors.primaryAccent,
                            inactiveTrackColor = colors.border
                        ),
                        modifier = Modifier.testTag("speech_speed_slider")
                    )
                }
            }

            // 3. History & About Group
            SettingsSectionCard(title = "Data & About") {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .padding(vertical = WhislyTokens.Space8)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = null,
                            tint = WhislyTokens.Error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Clear Translation History",
                            style = MaterialTheme.typography.titleSmall.copy(
                                color = colors.primaryCocoa,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    OutlinedButton(
                        onClick = onClearHistory,
                        shape = RoundedCornerShape(WhislyTokens.RadiusControl),
                        border = BorderStroke(1.dp, WhislyTokens.Error),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = WhislyTokens.Error),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("settings_clear_history_button")
                    ) {
                        Text("Clear")
                    }
                }

                HorizontalDivider(color = colors.border)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .clickable(onClick = onOpenAboutWhisly)
                        .padding(vertical = 12.dp)
                        .testTag("settings_open_about_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = WhislyTokens.PrimaryOrange,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "About Whisly",
                        style = MaterialTheme.typography.titleSmall.copy(
                            color = colors.primaryCocoa,
                            fontWeight = FontWeight.SemiBold
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = null,
                        tint = colors.mutedText
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    val colors = WhislyDesign.colors
    Surface(
        shape = RoundedCornerShape(WhislyTokens.RadiusCard),
        color = colors.cardSurface,
        border = BorderStroke(1.dp, colors.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = WhislyTokens.Space16, vertical = WhislyTokens.Space12)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge.copy(
                    color = colors.primaryAccent,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(modifier = Modifier.height(WhislyTokens.Space8))
            content()
        }
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    val colors = WhislyDesign.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .padding(vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = WhislyTokens.PrimaryOrange,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    color = colors.primaryCocoa,
                    fontWeight = FontWeight.SemiBold
                )
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = colors.primaryAccent,
                uncheckedThumbColor = colors.mutedText,
                uncheckedTrackColor = colors.background
            ),
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Preview(name = "Settings Screen - Light", showBackground = true)
@Composable
private fun SettingsScreenLightPreview() {
    WhislyTheme(darkTheme = false) {
        SettingsScreen(
            settings = AppSettingsEntity(),
            telemetry = AiEvaluationSuite.RuntimeTelemetrySnapshot(42L, 256L, 24L, 18L, 22L, 64L),
            usedModelStorageBytes = 512 * 1024L,
            onToggleOfflineOnly = {},
            onSpeechSpeedChange = {},
            onToggleAutoPlay = {},
            onToggleWifiOnly = {},
            onToggleCorrectionConsent = {},
            onClearHistory = {},
            onBack = {}
        )
    }
}

@Preview(name = "Settings Screen - Dark", showBackground = true)
@Composable
private fun SettingsScreenDarkPreview() {
    WhislyTheme(darkTheme = true) {
        SettingsScreen(
            settings = AppSettingsEntity(offlineOnlyMode = true),
            telemetry = AiEvaluationSuite.RuntimeTelemetrySnapshot(42L, 256L, 24L, 18L, 22L, 64L),
            usedModelStorageBytes = 512 * 1024L,
            onToggleOfflineOnly = {},
            onSpeechSpeedChange = {},
            onToggleAutoPlay = {},
            onToggleWifiOnly = {},
            onToggleCorrectionConsent = {},
            onClearHistory = {},
            onBack = {}
        )
    }
}
