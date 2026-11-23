package com.example.presentation.translate

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ai.pipeline.OrchestratorState
import com.example.components.HeroMicrophoneControl
import com.example.components.LanguageSelectorBar
import com.example.components.TranslationWorkspaceCard
import com.example.components.WhislyTopBar
import com.example.core.theme.WhislyDesign
import com.example.core.theme.WhislyTokens
import com.example.data.local.AppSettingsEntity
import com.example.data.local.LanguageEntity
import com.example.domain.models.PipelineErrorType
import com.example.domain.models.PipelineStage
import com.example.presentation.TranslateUiState

/**
 * Main Translate Screen (Phase 10).
 *
 * Strictly structured into 4 clean sections:
 * 1. Header (Whisly logo, single readiness status indicator, Theme toggle)
 * 2. Language row (selected spoken source + Swap + Target Language)
 * 3. Central microphone button + real audio waveform
 * 4. Translation result card (Original speech & Translation)
 */
@Composable
fun TranslateScreen(
    uiState: TranslateUiState,
    languages: List<LanguageEntity>,
    settings: AppSettingsEntity?,
    pipelineStage: PipelineStage,
    orchestratorState: OrchestratorState = OrchestratorState.Idle,
    amplitudes: List<Float>,
    isPlayingAudio: Boolean,
    hasMicPermission: Boolean,
    onSelectSource: (String) -> Unit,
    onSelectTarget: (String) -> Unit,
    onSwapLanguages: () -> Unit,
    onConfirmAmbiguousSourceLanguage: (String) -> Unit = {},
    onToggleRecording: () -> Unit,
    onPermissionDenied: () -> Unit,
    onTranscriptChanged: (String) -> Unit,
    onConfirmAndTranslate: () -> Unit,
    onTranslatedTextEdited: (String) -> Unit,
    onPlayAudio: () -> Unit,
    onStopAudio: () -> Unit,
    onSavePhrase: () -> Unit,
    onDismissError: () -> Unit,
    onInstallVoiceData: () -> Unit = {},
    onNavigateToLanguages: () -> Unit,
    onNavigateToTextMode: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onToggleDarkTheme: () -> Unit = {},
    onExitToHome: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val colors = WhislyDesign.colors
    val sourceLang = languages.find { it.code == uiState.sourceLangCode }
    val targetLang = languages.find { it.code == uiState.targetLangCode }
    val isPairVerifiedOffline = (sourceLang?.code == "en" || sourceLang?.isInstalled == true) &&
        (targetLang?.code == "en" || targetLang?.isInstalled == true)
    val ttsSupportedForTarget = targetLang?.canSpeak == true || targetLang?.ttsCapability == "SYSTEM_FALLBACK_ONLY"

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onToggleRecording()
        } else {
            onPermissionDenied()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("translate_home_screen")
    ) {
        // 1. Pinned Top Bar: Solid Deep Cocoa Brown WhislyTopBar
        WhislyTopBar(
            isOnline = uiState.isOnline,
            isPairVerifiedOffline = isPairVerifiedOffline,
            isDarkTheme = uiState.isDarkTheme,
            onToggleTheme = onToggleDarkTheme,
            onStatusClick = onNavigateToLanguages,
            onExitToHome = onExitToHome
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(14.dp))

            // 2. Language Row: user-selected spoken source + Swap + Target Language
            LanguageSelectorBar(
                sourceLanguage = sourceLang,
                targetLanguage = targetLang,
                allLanguages = languages,
                onSelectSource = onSelectSource,
                onSelectTarget = onSelectTarget,
                onSwapLanguages = onSwapLanguages,
                isSourceAutoDetect = false,
                allowAutoDetect = false
            )

            // Ambiguous Language Confirmation Banner (e.g., Hindi vs Marathi or Bengali vs Assamese)
            AnimatedVisibility(visible = uiState.ambiguousLanguageCandidates.size >= 2) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colors.elevatedSurface,
                    border = BorderStroke(1.dp, WhislyTokens.SecondaryPeach),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("ambiguous_lid_banner")
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Text(
                            text = "Confirm spoken language",
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = colors.primaryCocoa,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            uiState.ambiguousLanguageCandidates.take(2).forEach { code ->
                                val candidate = languages.find { it.code == code }
                                OutlinedButton(
                                    onClick = { onConfirmAmbiguousSourceLanguage(code) },
                                    shape = RoundedCornerShape(WhislyTokens.RadiusControl),
                                    modifier = Modifier.testTag("confirm_lid_$code")
                                ) {
                                    Text(
                                        text = candidate?.let { "${it.nativeName} (${it.englishName})" } ?: code,
                                        color = colors.primaryCocoa
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Honest Error or Missing Language Pack Banner
            val bannerErrorMessage = uiState.statusBannerMessage
                ?: uiState.activeError?.let { err ->
                    if (err.userMessage.isNotBlank()) err.userMessage else err.title
                }
            val showBanner = bannerErrorMessage != null || !isPairVerifiedOffline
            AnimatedVisibility(visible = showBanner) {
                Surface(
                    shape = RoundedCornerShape(WhislyTokens.RadiusButtonInput),
                    color = if (bannerErrorMessage != null) WhislyTokens.ErrorBg else WhislyTokens.WarningBg,
                    border = BorderStroke(
                        1.dp,
                        if (bannerErrorMessage != null) WhislyTokens.Error.copy(alpha = 0.25f) else WhislyTokens.WarningText.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = WhislyTokens.ScreenHorizontalPadding, vertical = WhislyTokens.Space8)
                        .testTag("error_or_pack_banner")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = WhislyTokens.Space16, vertical = WhislyTokens.Space12)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ErrorOutline,
                            contentDescription = null,
                            tint = if (bannerErrorMessage != null) WhislyTokens.Error else WhislyTokens.WarningText,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(WhislyTokens.Space12))
                        Text(
                            text = bannerErrorMessage
                                ?: "Download ${targetLang?.englishName ?: "pack"} to translate offline.",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = if (bannerErrorMessage != null) WhislyTokens.Error else colors.primaryCocoa,
                                fontWeight = FontWeight.Medium
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        if (!isPairVerifiedOffline) {
                            TextButton(
                                onClick = onNavigateToLanguages,
                                modifier = Modifier.testTag("banner_install_pack_button")
                            ) {
                                Text("Download", color = WhislyTokens.PrimaryOrange, fontWeight = FontWeight.Bold)
                            }
                        } else if (uiState.activeError == PipelineErrorType.UNSUPPORTED_TARGET_VOICE ||
                            uiState.activeError == PipelineErrorType.TTS_FAILURE
                        ) {
                            // Official system voice-data install action, not an app model download.
                            TextButton(
                                onClick = onInstallVoiceData,
                                modifier = Modifier.testTag("banner_install_voice_data_button")
                            ) {
                                Text("Install voice data", color = WhislyTokens.PrimaryOrange, fontWeight = FontWeight.Bold)
                            }
                        } else if (bannerErrorMessage != null) {
                            IconButton(onClick = onDismissError) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "Dismiss",
                                    tint = colors.mutedText
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 3. Central Microphone Control & Real Audio Waveform
            val statusHintText = when {
                !isPairVerifiedOffline -> "Download ${targetLang?.englishName ?: "pack"} to translate offline"
                pipelineStage == PipelineStage.IDLE -> ""
                else -> pipelineStage.statusLabel
            }
            HeroMicrophoneControl(
                isRecording = uiState.isRecording,
                recordingElapsedMs = uiState.recordingElapsedMs,
                amplitudes = amplitudes,
                statusHint = statusHintText,
                orchestratorState = orchestratorState,
                hasTranslationResult = uiState.translatedSentence.isNotBlank(),
                sourceLanguageName = sourceLang?.englishName ?: "Tamil",
                targetLanguageName = targetLang?.englishName ?: "Bengali",
                onToggleRecording = {
                    if (uiState.isRecording || hasMicPermission) {
                        onToggleRecording()
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onPermissionDenied = onPermissionDenied,
                lowEndOptimization = uiState.lowEndPerformanceMode
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 4. Translation Result Card (Original speech + Translation)
            val sourceDisplay = "${sourceLang?.nativeName ?: ""} (${sourceLang?.englishName ?: "Tamil"})"
            TranslationWorkspaceCard(
                stage = pipelineStage,
                sourceLanguageName = sourceDisplay,
                targetLanguageName = "${targetLang?.nativeName ?: ""} (${targetLang?.englishName ?: "Bengali"})",
                recognizedTranscript = uiState.recognizedTranscript,
                transcriptReviewNotice = uiState.transcriptReviewNotice,
                translatedSentence = uiState.translatedSentence,
                sttConfidence = uiState.sttConfidence,
                sttTierLabel = uiState.sttTierLabel,
                latencyBadgeText = "",
                isPlayingAudio = isPlayingAudio,
                ttsSupportedForTarget = ttsSupportedForTarget,
                onTranscriptChanged = onTranscriptChanged,
                onConfirmAndTranslate = onConfirmAndTranslate,
                onTranslatedTextEdited = onTranslatedTextEdited,
                onPlayAudio = onPlayAudio,
                onStopAudio = onStopAudio,
                onCopyTranslation = {
                    clipboard.setText(AnnotatedString(uiState.translatedSentence))
                },
                onSavePhrase = onSavePhrase
            )
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Translate Screen - Light", showBackground = true)
@Composable
private fun TranslateScreenLightPreview() {
    com.example.core.theme.WhislyTheme(darkTheme = false) {
        TranslateScreen(
            uiState = TranslateUiState(
                recognizedTranscript = "Sample text",
                translatedSentence = "Sample text"
            ),
            languages = com.example.ai.translation.IndicLanguageCatalog.buildInitialLanguageEntities().take(4),
            settings = AppSettingsEntity(),
            pipelineStage = PipelineStage.COMPLETED,
            amplitudes = listOf(0.2f, 0.5f, 0.8f, 0.4f, 0.3f),
            isPlayingAudio = false,
            hasMicPermission = true,
            onSelectSource = {},
            onSelectTarget = {},
            onSwapLanguages = {},
            onToggleRecording = {},
            onPermissionDenied = {},
            onTranscriptChanged = {},
            onConfirmAndTranslate = {},
            onTranslatedTextEdited = {},
            onPlayAudio = {},
            onStopAudio = {},
            onSavePhrase = {},
            onDismissError = {},
            onNavigateToLanguages = {},
            onNavigateToTextMode = {},
            onNavigateToHistory = {}
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Translate Screen - Dark", showBackground = true)
@Composable
private fun TranslateScreenDarkPreview() {
    com.example.core.theme.WhislyTheme(darkTheme = true) {
        TranslateScreen(
            uiState = TranslateUiState(
                isDarkTheme = true,
                recognizedTranscript = "Sample text",
                translatedSentence = "Sample text"
            ),
            languages = com.example.ai.translation.IndicLanguageCatalog.buildInitialLanguageEntities().take(4),
            settings = AppSettingsEntity(),
            pipelineStage = PipelineStage.COMPLETED,
            amplitudes = listOf(0.2f, 0.5f, 0.8f, 0.4f, 0.3f),
            isPlayingAudio = false,
            hasMicPermission = true,
            onSelectSource = {},
            onSelectTarget = {},
            onSwapLanguages = {},
            onToggleRecording = {},
            onPermissionDenied = {},
            onTranscriptChanged = {},
            onConfirmAndTranslate = {},
            onTranslatedTextEdited = {},
            onPlayAudio = {},
            onStopAudio = {},
            onSavePhrase = {},
            onDismissError = {},
            onNavigateToLanguages = {},
            onNavigateToTextMode = {},
            onNavigateToHistory = {}
        )
    }
}
