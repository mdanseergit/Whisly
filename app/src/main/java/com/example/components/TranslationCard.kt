package com.example.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.theme.WhislyDesign
import com.example.core.theme.WhislyTokens
import com.example.domain.models.PipelineStage

@Composable
fun TranslationWorkspaceCard(
    stage: PipelineStage,
    sourceLanguageName: String,
    targetLanguageName: String,
    recognizedTranscript: String,
    translatedSentence: String,
    sttConfidence: Float?,
    latencyBadgeText: String,
    isPlayingAudio: Boolean,
    ttsSupportedForTarget: Boolean,
    onTranscriptChanged: (String) -> Unit,
    onConfirmAndTranslate: () -> Unit,
    onTranslatedTextEdited: (String) -> Unit,
    onPlayAudio: () -> Unit,
    onStopAudio: () -> Unit,
    onCopyTranslation: () -> Unit,
    onSavePhrase: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = WhislyDesign.colors
    var editingTranslation by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(WhislyTokens.RadiusMajorSurface),
        color = colors.cardSurface,
        border = BorderStroke(1.dp, colors.border),
        shadowElevation = 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = WhislyTokens.ScreenHorizontalPadding)
            .testTag("translation_result_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(WhislyTokens.Space16)
        ) {
            // Section 1: Recognized Speech + Pre-Translation Confirmation
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.GraphicEq,
                        contentDescription = null,
                        tint = colors.primaryAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Original Speech • $sourceLanguageName",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = colors.mutedText,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }

                if (sttConfidence != null && recognizedTranscript.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = WhislyTokens.SuccessBg
                    ) {
                        Text(
                            text = "STT ${(sttConfidence * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = WhislyTokens.Success,
                                fontWeight = FontWeight.SemiBold
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(WhislyTokens.Space8))

            OutlinedTextField(
                value = recognizedTranscript,
                onValueChange = onTranscriptChanged,
                placeholder = {
                    Text(
                        text = "Speak with the microphone or type/edit recognized speech here before translating…",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = colors.mutedText.copy(alpha = 0.7f)
                        )
                    )
                },
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = colors.primaryCocoa
                ),
                shape = RoundedCornerShape(WhislyTokens.RadiusButtonInput),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.primaryAccent,
                    unfocusedBorderColor = colors.border,
                    focusedContainerColor = colors.background,
                    unfocusedContainerColor = colors.background
                ),
                minLines = 2,
                maxLines = 4,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("transcript_confirmation_input")
            )

            Spacer(modifier = Modifier.height(WhislyTokens.Space12))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onConfirmAndTranslate,
                    enabled = recognizedTranscript.isNotBlank(),
                    shape = RoundedCornerShape(WhislyTokens.RadiusControl),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primaryAccent,
                        contentColor = WhislyTokens.WarmWhite
                    ),
                    modifier = Modifier
                        .height(48.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("confirm_and_translate_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (stage == PipelineStage.AWAITING_CONFIRMATION) {
                            "Confirm & Translate"
                        } else {
                            "Translate"
                        },
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = WhislyTokens.WarmWhite,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(WhislyTokens.Space16))
            HorizontalDivider(color = colors.border)
            Spacer(modifier = Modifier.height(WhislyTokens.Space16))

            // Section 2: Target Translation Result + Playback & Save Controls
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Translation • $targetLanguageName",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = colors.primaryAccent,
                        fontWeight = FontWeight.Bold
                    )
                )

                if (translatedSentence.isNotBlank()) {
                    TextButton(
                        onClick = { editingTranslation = !editingTranslation },
                        modifier = Modifier.testTag("edit_translation_toggle")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "Edit translation before speaking",
                            tint = colors.mutedText,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (editingTranslation) "Done" else "Edit",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = colors.mutedText
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(WhislyTokens.Space8))

            if (editingTranslation) {
                OutlinedTextField(
                    value = translatedSentence,
                    onValueChange = onTranslatedTextEdited,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = colors.primaryCocoa
                    ),
                    shape = RoundedCornerShape(WhislyTokens.RadiusButtonInput),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primaryAccent,
                        unfocusedBorderColor = colors.border,
                        focusedContainerColor = colors.background,
                        unfocusedContainerColor = colors.background
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_translation_input")
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(WhislyTokens.RadiusButtonInput),
                    color = colors.elevatedSurface,
                    border = BorderStroke(1.dp, colors.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = translatedSentence.ifBlank {
                            "Translation will appear here after you confirm the recognized transcript."
                        },
                        style = if (translatedSentence.isBlank()) {
                            MaterialTheme.typography.bodyMedium.copy(color = colors.mutedText)
                        } else {
                            MaterialTheme.typography.titleLarge.copy(
                                color = colors.primaryCocoa,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        modifier = Modifier
                            .padding(WhislyTokens.Space16)
                            .testTag("translated_output_text")
                    )
                }
            }

            Spacer(modifier = Modifier.height(WhislyTokens.Space12))

            // Action Toolbar: Play Voice / Copy / Save to Phrasebook
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(WhislyTokens.Space8),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (ttsSupportedForTarget) {
                    Button(
                        onClick = if (isPlayingAudio) onStopAudio else onPlayAudio,
                        enabled = translatedSentence.isNotBlank(),
                        shape = RoundedCornerShape(WhislyTokens.RadiusControl),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isPlayingAudio) WhislyTokens.Error else colors.primaryAccent,
                            contentColor = WhislyTokens.WarmWhite
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("play_translated_speech_button")
                    ) {
                        Icon(
                            imageVector = if (isPlayingAudio) Icons.Filled.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = if (isPlayingAudio) "Stop Speech" else "Play Speech",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isPlayingAudio) "Stop" else "Speak",
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = WhislyTokens.WarmWhite,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(WhislyTokens.RadiusControl),
                        color = WhislyTokens.WarningBg,
                        border = BorderStroke(1.dp, WhislyTokens.WarningText.copy(alpha = 0.25f)),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tts_unavailable_notice")
                    ) {
                        Text(
                            text = "Offline Voice Unavailable",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = WhislyTokens.WarningText
                            ),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
                        )
                    }
                }

                OutlinedButton(
                    onClick = onCopyTranslation,
                    enabled = translatedSentence.isNotBlank(),
                    shape = RoundedCornerShape(WhislyTokens.RadiusControl),
                    border = BorderStroke(1.dp, colors.border),
                    modifier = Modifier
                        .height(48.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("copy_translation_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = "Copy Translation",
                        tint = colors.primaryCocoa,
                        modifier = Modifier.size(18.dp)
                    )
                }

                OutlinedButton(
                    onClick = onSavePhrase,
                    enabled = translatedSentence.isNotBlank(),
                    shape = RoundedCornerShape(WhislyTokens.RadiusControl),
                    border = BorderStroke(1.dp, colors.border),
                    modifier = Modifier
                        .height(48.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("save_phrase_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.BookmarkAdd,
                        contentDescription = "Save to Phrasebook",
                        tint = colors.primaryCocoa,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
