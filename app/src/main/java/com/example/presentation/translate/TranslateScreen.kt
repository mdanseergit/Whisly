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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.components.HeroMicrophoneControl
import com.example.components.LanguageSelectorBar
import com.example.components.TranslationWorkspaceCard
import com.example.components.WhislyTopBar
import com.example.core.theme.WhislyDesign
import com.example.core.theme.WhislyTokens
import com.example.data.local.AppSettingsEntity
import com.example.data.local.LanguageEntity
import com.example.domain.models.PipelineStage
import com.example.presentation.TranslateUiState

/**
 * Main Translate Screen (Phase 10).
 *
 * Strictly structured into 4 clean sections:
 * 1. Header (Whisly logo, single readiness status indicator, Theme toggle)
 * 2. Language row (Source Language / Auto-detect + Swap + Target Language)
 * 3. Central microphone button + real audio waveform
 * 4. Translation result card (Original speech & Translation)
 */
@Composable
fun TranslateScreen(
    uiState: TranslateUiState,
    languages: List<LanguageEntity>,
    settings: AppSettingsEntity?,
    pipelineStage: PipelineStage,
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
    onNavigateToLanguages: () -> Unit,
    onNavigateToTextMode: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onToggleDarkTheme: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    val colors = WhislyDesign.colors
    val isSourceAuto = uiState.sourceLangCode == "auto"
    val sourceLang = languages.find { it.code == uiState.sourceLangCode }
    val targetLang = languages.find { it.code == uiState.targetLangCode }
    val detectedLanguageLabel = uiState.detectedSourceLangCode?.let { detectedCode ->
        languages.find { it.code == detectedCode }?.englishName ?: detectedCode
    } ?: ""
    val isPairVerifiedOffline = (isSourceAuto || sourceLang?.isInstalled == true) && (targetLang?.isInstalled == true)
    val ttsSupportedForTarget = targetLang?.canSpeak == true || targetLang?.ttsCapability == "VITS_RASA_VERIFIED"

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
            onStatusClick = onNavigateToLanguages
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(14.dp))

            // 2. Language Row: Source Language / Auto-detect + Swap + Target Language
            LanguageSelectorBar(
                sourceLanguage = sourceLang,
                targetLanguage = targetLang,
                allLanguages = languages,
                onSelectSource = onSelectSource,
                onSelectTarget = onSelectTarget,
                onSwapLanguages = onSwapLanguages,
                isSourceAutoDetect = isSourceAuto,
                detectedLanguageName = detectedLanguageLabel
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
            AnimatedVisibility(visible = uiState.activeError != null || !isPairVerifiedOffline) {
                val err = uiState.activeError
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (err != null) WhislyTokens.ErrorBg else WhislyTokens.WarningBg,
                    border = BorderStroke(
                        1.dp,
                        if (err != null) WhislyTokens.Error.copy(alpha = 0.3f) else WhislyTokens.WarningText.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("error_or_pack_banner")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ErrorOutline,
                            contentDescription = null,
                            tint = if (err != null) WhislyTokens.Error else WhislyTokens.WarningText,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = err?.title ?: "Download language to use offline",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    color = if (err != null) WhislyTokens.Error else WhislyTokens.WarningText
                                )
                            )
                            Text(
                                text = err?.userMessage
                                    ?: "Download ${sourceLang?.englishName ?: ""} and ${targetLang?.englishName ?: ""} to translate offline.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = colors.primaryCocoa
                                )
                            )
                        }
                        if (!isPairVerifiedOffline) {
                            TextButton(
                                onClick = onNavigateToLanguages,
                                modifier = Modifier.testTag("banner_install_pack_button")
                            ) {
                                Text("Download", color = WhislyTokens.PrimaryOrange, fontWeight = FontWeight.Bold)
                            }
                        } else if (err != null) {
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
            HeroMicrophoneControl(
                isRecording = uiState.isRecording,
                recordingElapsedMs = uiState.recordingElapsedMs,
                amplitudes = amplitudes,
                statusHint = if (pipelineStage == PipelineStage.IDLE) "" else pipelineStage.statusLabel,
                sourceLanguageName = if (isSourceAuto) "Auto-detect" else (sourceLang?.englishName ?: "Tamil"),
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
            val sourceDisplay = when {
                isSourceAuto && detectedLanguageLabel.isNotBlank() ->
                    "Auto ($detectedLanguageLabel)"
                isSourceAuto -> "Auto-detect"
                else -> "${sourceLang?.nativeName ?: ""} (${sourceLang?.englishName ?: "Tamil"})"
            }
            TranslationWorkspaceCard(
                stage = pipelineStage,
                sourceLanguageName = sourceDisplay,
                targetLanguageName = "${targetLang?.nativeName ?: ""} (${targetLang?.englishName ?: "Bengali"})",
                recognizedTranscript = uiState.recognizedTranscript,
                translatedSentence = uiState.translatedSentence,
                sttConfidence = uiState.sttConfidence,
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
                recognizedTranscript = "வணக்கம், நீங்கள் எப்படி இருக்கிறீர்கள்?",
                translatedSentence = "নমস্কার, আপনি কেমন আছেন?"
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
            onNavigateToHistory = {},
            onNavigateToSettings = {}
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
                recognizedTranscript = "வணக்கம், நீங்கள் எப்படி இருக்கிறீர்கள்?",
                translatedSentence = "নমস্কার, আপনি কেমন আছেন?"
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
            onNavigateToHistory = {},
            onNavigateToSettings = {}
        )
    }
}
