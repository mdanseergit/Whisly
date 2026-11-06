package com.example.presentation.text_translation

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.ai.translation.IndicLanguageCatalog
import com.example.components.LanguageSelectorBar
import com.example.core.theme.WhislyDesign
import com.example.core.theme.WhislyTheme
import com.example.core.theme.WhislyTokens
import com.example.data.local.LanguageEntity
import com.example.presentation.TranslateUiState

@Composable
fun TextTranslationScreen(
    uiState: TranslateUiState,
    languages: List<LanguageEntity>,
    onSelectSource: (String) -> Unit,
    onSelectTarget: (String) -> Unit,
    onSwapLanguages: () -> Unit,
    onInputChange: (String) -> Unit,
    onTranslateClick: () -> Unit,
    onClearInput: () -> Unit,
    onListenResult: () -> Unit,
    onSavePhrase: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
    val clipboard = LocalClipboardManager.current
    val colors = WhislyDesign.colors
    val sourceLang = languages.find { it.code == uiState.sourceLangCode }
    val targetLang = languages.find { it.code == uiState.targetLangCode }
    val ttsSupported = targetLang?.ttsCapability == "VITS_RASA_VERIFIED"

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = WhislyTokens.Space24)
            .testTag("text_translation_screen")
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
                    .testTag("text_mode_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to Voice Translation",
                    tint = colors.primaryCocoa
                )
            }
            Text(
                text = "Text Translation",
                style = MaterialTheme.typography.titleLarge.copy(
                    color = colors.primaryCocoa,
                    fontWeight = FontWeight.Bold
                )
            )
        }

        LanguageSelectorBar(
            sourceLanguage = sourceLang,
            targetLanguage = targetLang,
            allLanguages = languages,
            onSelectSource = onSelectSource,
            onSelectTarget = onSelectTarget,
            onSwapLanguages = onSwapLanguages
        )

        Spacer(modifier = Modifier.height(WhislyTokens.Space16))

        Surface(
            shape = RoundedCornerShape(WhislyTokens.RadiusCard),
            color = colors.cardSurface,
            border = BorderStroke(1.dp, colors.border),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = WhislyTokens.ScreenHorizontalPadding)
        ) {
            Column(modifier = Modifier.padding(WhislyTokens.Space16)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "${sourceLang?.nativeName ?: ""} (${sourceLang?.englishName ?: "Source"})",
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = colors.mutedText
                        )
                    )
                    if (uiState.textModeInput.isNotEmpty()) {
                        TextButton(
                            onClick = onClearInput,
                            modifier = Modifier.testTag("clear_text_input_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Clear,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clear")
                        }
                    }
                }

                OutlinedTextField(
                    value = uiState.textModeInput,
                    onValueChange = onInputChange,
                    placeholder = {
                        Text("Enter ${sourceLang?.englishName ?: "source"} text to translate…")
                    },
                    minLines = 3,
                    maxLines = 6,
                    shape = RoundedCornerShape(WhislyTokens.RadiusButtonInput),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primaryAccent,
                        unfocusedBorderColor = colors.border,
                        focusedContainerColor = colors.background,
                        unfocusedContainerColor = colors.background
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("text_mode_input_field")
                )

                Spacer(modifier = Modifier.height(WhislyTokens.Space12))

                Button(
                    onClick = onTranslateClick,
                    enabled = uiState.textModeInput.isNotBlank(),
                    shape = RoundedCornerShape(WhislyTokens.RadiusButtonInput),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primaryAccent,
                        contentColor = WhislyTokens.WarmWhite
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("text_mode_translate_button")
                ) {
                    Icon(imageVector = Icons.Outlined.Translate, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Translate",
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = WhislyTokens.WarmWhite,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }

        if (uiState.textModeOutput.isNotBlank()) {
            Spacer(modifier = Modifier.height(WhislyTokens.Space16))
            Surface(
                shape = RoundedCornerShape(WhislyTokens.RadiusCard),
                color = colors.cardSurface,
                border = BorderStroke(1.dp, WhislyTokens.SecondaryPeach),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = WhislyTokens.ScreenHorizontalPadding)
                    .testTag("text_mode_output_card")
            ) {
                Column(modifier = Modifier.padding(WhislyTokens.Space16)) {
                    Text(
                        text = "${targetLang?.nativeName ?: ""} (${targetLang?.englishName ?: "Target"})",
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = colors.primaryAccent,
                            fontWeight = FontWeight.Bold
                        )
                    )

                    Spacer(modifier = Modifier.height(WhislyTokens.Space8))

                    Text(
                        text = uiState.textModeOutput,
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = colors.primaryCocoa,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.testTag("text_mode_output_value")
                    )

                    Spacer(modifier = Modifier.height(WhislyTokens.Space12))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(WhislyTokens.Space8),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = onListenResult,
                            enabled = ttsSupported,
                            shape = RoundedCornerShape(WhislyTokens.RadiusControl),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.primaryAccent,
                                contentColor = WhislyTokens.WarmWhite
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("text_mode_listen_button")
                        ) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (ttsSupported) "Speak" else "Text Only")
                        }

                        OutlinedButton(
                            onClick = { clipboard.setText(AnnotatedString(uiState.textModeOutput)) },
                            shape = RoundedCornerShape(WhislyTokens.RadiusControl),
                            border = BorderStroke(1.dp, colors.border),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("text_mode_copy_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = "Copy Translation",
                                tint = colors.primaryCocoa
                            )
                        }

                        OutlinedButton(
                            onClick = onSavePhrase,
                            shape = RoundedCornerShape(WhislyTokens.RadiusControl),
                            border = BorderStroke(1.dp, colors.border),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("text_mode_save_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.BookmarkAdd,
                                contentDescription = "Save Translation",
                                tint = colors.primaryCocoa
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(name = "Text Translation Screen - Light", showBackground = true)
@Composable
private fun TextTranslationScreenLightPreview() {
    WhislyTheme(darkTheme = false) {
        TextTranslationScreen(
            uiState = TranslateUiState(
                textModeInput = "வணக்கம், நீங்கள் எப்படி இருக்கிறீர்கள்?",
                textModeOutput = "নমস্কার, আপনি কেমন আছেন?"
            ),
            languages = IndicLanguageCatalog.buildInitialLanguageEntities().take(4),
            onSelectSource = {},
            onSelectTarget = {},
            onSwapLanguages = {},
            onInputChange = {},
            onTranslateClick = {},
            onClearInput = {},
            onListenResult = {},
            onSavePhrase = {},
            onBack = {}
        )
    }
}

@Preview(name = "Text Translation Screen - Dark", showBackground = true)
@Composable
private fun TextTranslationScreenDarkPreview() {
    WhislyTheme(darkTheme = true) {
        TextTranslationScreen(
            uiState = TranslateUiState(
                isDarkTheme = true,
                textModeInput = "வணக்கம், நீங்கள் எப்படி இருக்கிறீர்கள்?",
                textModeOutput = "নমস্কার, আপনি কেমন আছেন?"
            ),
            languages = IndicLanguageCatalog.buildInitialLanguageEntities().take(4),
            onSelectSource = {},
            onSelectTarget = {},
            onSwapLanguages = {},
            onInputChange = {},
            onTranslateClick = {},
            onClearInput = {},
            onListenResult = {},
            onSavePhrase = {},
            onBack = {}
        )
    }
}
