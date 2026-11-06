package com.example.presentation.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.OfflineBolt
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ai.evaluation.AiEvaluationSuite
import com.example.core.theme.WhislyDesign
import com.example.core.theme.WhislyTokens
import com.example.data.local.AppSettingsEntity
import com.example.data.local.LanguageEntity
import java.util.Locale

/**
 * PHASE 4 & 5 — User Profile Screen (`ProfileScreen.kt`).
 *
 * - iOS-style grouped lists (rounded group `20dp`, leading icon, Bold/SemiBold title, trailing control/chevron)
 * - No redundant multi-line descriptions under toggles or action rows
 * - Direct navigation row to `AboutWhislyScreen` (`profile_open_about_button`)
 */
@Composable
fun ProfileScreen(
    settings: AppSettingsEntity?,
    languages: List<LanguageEntity>,
    telemetry: AiEvaluationSuite.RuntimeTelemetrySnapshot,
    isDarkTheme: Boolean,
    lowEndPerformanceMode: Boolean,
    onToggleDarkTheme: (Boolean) -> Unit,
    onToggleLowEndMode: (Boolean) -> Unit,
    onToggleOfflineOnly: (Boolean) -> Unit,
    onSpeechSpeedChange: (Float) -> Unit,
    onOpenLanguagesManager: () -> Unit,
    onOpenSavedPhrases: () -> Unit,
    onOpenAdvancedSettings: () -> Unit,
    onOpenAboutWhisly: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = WhislyDesign.colors
    val currentSettings = settings ?: AppSettingsEntity()
    val installedCount = languages.count { it.isInstalled }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                horizontal = WhislyTokens.ScreenHorizontalPadding,
                vertical = WhislyTokens.Space16
            )
            .testTag("profile_screen")
    ) {
        Text(
            text = "Profile",
            style = MaterialTheme.typography.headlineMedium.copy(
                color = colors.primaryCocoa,
                fontWeight = FontWeight.Bold
            )
        )

        Spacer(modifier = Modifier.height(WhislyTokens.Space16))

        // 1. On-Device Voice Summary Card
        Surface(
            shape = RoundedCornerShape(WhislyTokens.RadiusCard),
            color = colors.cardSurface,
            border = BorderStroke(1.dp, colors.border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(WhislyTokens.Space16)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(WhislyTokens.PrimaryOrange.copy(alpha = 0.12f))
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = null,
                        tint = WhislyTokens.PrimaryOrange,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(WhislyTokens.Space12))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "On-Device Voice Profile",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = colors.primaryCocoa,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = WhislyTokens.Success,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$installedCount language packs installed",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = WhislyTokens.Success
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(WhislyTokens.Space16))

        // 2. iOS-Style Grouped List: Preferences
        Surface(
            shape = RoundedCornerShape(WhislyTokens.RadiusCard),
            color = colors.cardSurface,
            border = BorderStroke(1.dp, colors.border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = WhislyTokens.Space16, vertical = WhislyTokens.Space8)) {
                ProfileSwitchRow(
                    icon = Icons.Outlined.DarkMode,
                    title = "Dark Theme",
                    checked = isDarkTheme,
                    onCheckedChange = onToggleDarkTheme,
                    testTag = "profile_dark_theme_switch"
                )

                HorizontalDivider(color = colors.border)

                ProfileSwitchRow(
                    icon = Icons.Outlined.Speed,
                    title = "Low-RAM & Battery Saver",
                    checked = lowEndPerformanceMode,
                    onCheckedChange = onToggleLowEndMode,
                    testTag = "profile_low_end_mode_switch"
                )

                HorizontalDivider(color = colors.border)

                ProfileSwitchRow(
                    icon = Icons.Outlined.OfflineBolt,
                    title = "Offline-Only Mode",
                    checked = currentSettings.offlineOnlyMode,
                    onCheckedChange = onToggleOfflineOnly,
                    testTag = "profile_offline_only_switch"
                )
            }
        }

        Spacer(modifier = Modifier.height(WhislyTokens.Space16))

        // 3. iOS-Style Grouped List: Voice Speed & Navigation
        Surface(
            shape = RoundedCornerShape(WhislyTokens.RadiusCard),
            color = colors.cardSurface,
            border = BorderStroke(1.dp, colors.border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = WhislyTokens.Space16, vertical = WhislyTokens.Space12)) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Voice Playback Speed",
                        style = MaterialTheme.typography.titleSmall.copy(
                            color = colors.primaryCocoa,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Text(
                        text = String.format(Locale.US, "%.2fx", currentSettings.speechSpeed),
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = WhislyTokens.PrimaryOrange,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
                Slider(
                    value = currentSettings.speechSpeed,
                    onValueChange = onSpeechSpeedChange,
                    valueRange = 0.7f..1.4f,
                    colors = SliderDefaults.colors(
                        thumbColor = WhislyTokens.PrimaryOrange,
                        activeTrackColor = WhislyTokens.PrimaryOrange,
                        inactiveTrackColor = colors.border
                    )
                )

                HorizontalDivider(color = colors.border)

                ProfileActionRow(
                    icon = Icons.Outlined.Language,
                    title = "Language Packs",
                    onClick = onOpenLanguagesManager,
                    testTag = "profile_open_languages_button"
                )

                HorizontalDivider(color = colors.border)

                ProfileActionRow(
                    icon = Icons.Outlined.BookmarkBorder,
                    title = "Saved Phrases",
                    onClick = onOpenSavedPhrases,
                    testTag = "profile_open_saved_button"
                )

                HorizontalDivider(color = colors.border)

                ProfileActionRow(
                    icon = Icons.Outlined.Settings,
                    title = "Settings & Privacy",
                    onClick = onOpenAdvancedSettings,
                    testTag = "profile_open_settings_button"
                )

                HorizontalDivider(color = colors.border)

                ProfileActionRow(
                    icon = Icons.Outlined.Info,
                    title = "About Whisly",
                    onClick = onOpenAboutWhisly,
                    testTag = "profile_open_about_button"
                )
            }
        }
    }
}

@Composable
private fun ProfileSwitchRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    val colors = WhislyDesign.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .padding(vertical = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = WhislyTokens.PrimaryOrange,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(
                color = colors.primaryCocoa,
                fontWeight = FontWeight.SemiBold
            ),
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = WhislyTokens.PrimaryOrange,
                uncheckedThumbColor = colors.mutedText,
                uncheckedTrackColor = colors.background
            ),
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun ProfileActionRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    testTag: String
) {
    val colors = WhislyDesign.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
            .testTag(testTag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = WhislyTokens.PrimaryOrange,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
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

@androidx.compose.ui.tooling.preview.Preview(name = "Profile Screen - Light", showBackground = true)
@Composable
private fun ProfileScreenLightPreview() {
    com.example.core.theme.WhislyTheme(darkTheme = false) {
        ProfileScreen(
            settings = AppSettingsEntity(),
            languages = com.example.ai.translation.IndicLanguageCatalog.buildInitialLanguageEntities().take(4),
            telemetry = AiEvaluationSuite.RuntimeTelemetrySnapshot(42L, 256L, 24L, 18L, 22L, 64L),
            isDarkTheme = false,
            lowEndPerformanceMode = false,
            onToggleDarkTheme = {},
            onToggleLowEndMode = {},
            onToggleOfflineOnly = {},
            onSpeechSpeedChange = {},
            onOpenLanguagesManager = {},
            onOpenSavedPhrases = {},
            onOpenAdvancedSettings = {},
            onOpenAboutWhisly = {}
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Profile Screen - Dark", showBackground = true)
@Composable
private fun ProfileScreenDarkPreview() {
    com.example.core.theme.WhislyTheme(darkTheme = true) {
        ProfileScreen(
            settings = AppSettingsEntity(),
            languages = com.example.ai.translation.IndicLanguageCatalog.buildInitialLanguageEntities().take(4),
            telemetry = AiEvaluationSuite.RuntimeTelemetrySnapshot(42L, 256L, 24L, 18L, 22L, 64L),
            isDarkTheme = true,
            lowEndPerformanceMode = false,
            onToggleDarkTheme = {},
            onToggleLowEndMode = {},
            onToggleOfflineOnly = {},
            onSpeechSpeedChange = {},
            onOpenLanguagesManager = {},
            onOpenSavedPhrases = {},
            onOpenAdvancedSettings = {},
            onOpenAboutWhisly = {}
        )
    }
}
