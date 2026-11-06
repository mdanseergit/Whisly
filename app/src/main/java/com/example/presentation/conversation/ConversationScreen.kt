package com.example.presentation.conversation

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Forum
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.ai.translation.IndicLanguageCatalog
import com.example.components.LanguageSelectorBar
import com.example.core.theme.WhislyDesign
import com.example.core.theme.WhislyTheme
import com.example.core.theme.WhislyTokens
import com.example.data.local.LanguageEntity
import com.example.domain.models.ConversationTurn
import com.example.presentation.TranslateUiState

@Composable
fun ConversationScreen(
    uiState: TranslateUiState,
    languages: List<LanguageEntity>,
    onSelectSource: (String) -> Unit,
    onSelectTarget: (String) -> Unit,
    onSwapLanguages: () -> Unit,
    onSpeakerTurnAction: (Boolean) -> Unit,
    onSubmitTextTurn: (Boolean, String) -> Unit,
    onPlayTurnAudio: (ConversationTurn) -> Unit,
    onClearConversation: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = WhislyDesign.colors
    val sourceLang = languages.find { it.code == uiState.sourceLangCode }
    val targetLang = languages.find { it.code == uiState.targetLangCode }

    var quickTurnInput by remember { mutableStateOf("") }
    var activeInputSpeakerIsA by remember { mutableStateOf(true) }
    var pendingSpeakerIsA by remember { mutableStateOf(true) }

    val micPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onSpeakerTurnAction(pendingSpeakerIsA)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = WhislyTokens.Space12)
            .testTag("conversation_screen")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = WhislyTokens.ScreenHorizontalPadding)
        ) {
            Text(
                text = "Conversation",
                style = MaterialTheme.typography.headlineMedium.copy(
                    color = colors.primaryCocoa,
                    fontWeight = FontWeight.Bold
                )
            )
            if (uiState.conversationTurns.isNotEmpty()) {
                IconButton(
                    onClick = onClearConversation,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("clear_conversation_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteSweep,
                        contentDescription = "Clear Conversation",
                        tint = colors.mutedText
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(WhislyTokens.Space12))

        LanguageSelectorBar(
            sourceLanguage = sourceLang,
            targetLanguage = targetLang,
            allLanguages = languages,
            onSelectSource = onSelectSource,
            onSelectTarget = onSelectTarget,
            onSwapLanguages = onSwapLanguages
        )

        Spacer(modifier = Modifier.height(WhislyTokens.Space12))

        // Turn List or Empty State
        if (uiState.conversationTurns.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(WhislyTokens.RadiusMajorSurface),
                color = colors.cardSurface,
                border = BorderStroke(1.dp, colors.border),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = WhislyTokens.ScreenHorizontalPadding)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(WhislyTokens.Space24)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Forum,
                        contentDescription = null,
                        tint = colors.primaryAccent,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(WhislyTokens.Space8))
                    Text(
                        text = "Start a conversation",
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = colors.primaryCocoa,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = Modifier.height(WhislyTokens.Space4))
                    Text(
                        text = "Tap Person A or Person B below to speak.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = colors.mutedText
                        ),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    horizontal = WhislyTokens.ScreenHorizontalPadding,
                    vertical = WhislyTokens.Space8
                ),
                verticalArrangement = Arrangement.spacedBy(WhislyTokens.Space12),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("conversation_turn_list")
            ) {
                items(uiState.conversationTurns, key = { it.id }) { turn ->
                    ConversationTurnBubble(
                        turn = turn,
                        onPlayAudio = { onPlayTurnAudio(turn) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(WhislyTokens.Space8))

        // Bottom Dual-Speaker Control Deck
        Surface(
            shape = RoundedCornerShape(
                topStart = WhislyTokens.RadiusMajorSurface,
                topEnd = WhislyTokens.RadiusMajorSurface
            ),
            color = colors.cardSurface,
            border = BorderStroke(1.dp, colors.border),
            shadowElevation = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = WhislyTokens.ScreenHorizontalPadding, vertical = WhislyTokens.Space12)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(WhislyTokens.Space8),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = quickTurnInput,
                        onValueChange = { quickTurnInput = it },
                        placeholder = {
                            val speakerLang = if (activeInputSpeakerIsA) {
                                sourceLang?.englishName ?: "Source"
                            } else {
                                targetLang?.englishName ?: "Target"
                            }
                            Text("Type as ${if (activeInputSpeakerIsA) "Person A" else "Person B"} ($speakerLang)…")
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(WhislyTokens.RadiusButtonInput),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.primaryAccent,
                            unfocusedBorderColor = colors.border,
                            focusedContainerColor = colors.background,
                            unfocusedContainerColor = colors.background
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("conversation_text_input")
                    )

                    IconButton(
                        onClick = {
                            if (quickTurnInput.isNotBlank()) {
                                onSubmitTextTurn(activeInputSpeakerIsA, quickTurnInput)
                                quickTurnInput = ""
                                activeInputSpeakerIsA = !activeInputSpeakerIsA
                            }
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("conversation_send_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send Turn",
                            tint = colors.primaryAccent
                        )
                    }
                }

                Spacer(modifier = Modifier.height(WhislyTokens.Space8))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(WhislyTokens.Space12),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val isRecA = uiState.isRecording && uiState.activeConversationSpeakerIsA
                    val isRecB = uiState.isRecording && !uiState.activeConversationSpeakerIsA

                    Button(
                        onClick = {
                            activeInputSpeakerIsA = true
                            pendingSpeakerIsA = true
                            if (uiState.isRecording) {
                                onSpeakerTurnAction(true)
                            } else {
                                micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        shape = RoundedCornerShape(WhislyTokens.RadiusButtonInput),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRecA) WhislyTokens.Error else colors.primaryAccent,
                            contentColor = WhislyTokens.WarmWhite
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("speaker_a_mic_button")
                    ) {
                        Icon(
                            imageVector = if (isRecA) Icons.Filled.Stop else Icons.Filled.Mic,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isRecA) "Stop A" else "A: ${sourceLang?.nativeName ?: "தமிழ்"}",
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = WhislyTokens.WarmWhite,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            activeInputSpeakerIsA = false
                            pendingSpeakerIsA = false
                            if (uiState.isRecording) {
                                onSpeakerTurnAction(false)
                            } else {
                                micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        shape = RoundedCornerShape(WhislyTokens.RadiusButtonInput),
                        border = BorderStroke(
                            1.5.dp,
                            if (isRecB) WhislyTokens.Error else WhislyTokens.SecondaryPeach
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isRecB) WhislyTokens.ErrorBg else colors.elevatedSurface,
                            contentColor = colors.primaryCocoa
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("speaker_b_mic_button")
                    ) {
                        Icon(
                            imageVector = if (isRecB) Icons.Filled.Stop else Icons.Filled.Mic,
                            contentDescription = null,
                            tint = if (isRecB) WhislyTokens.Error else colors.primaryCocoa,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isRecB) "Stop B" else "B: ${targetLang?.nativeName ?: "বাংলা"}",
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = colors.primaryCocoa,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationTurnBubble(
    turn: ConversationTurn,
    onPlayAudio: () -> Unit
) {
    val colors = WhislyDesign.colors
    val bubbleBg = if (turn.isPersonA) {
        colors.cardSurface
    } else {
        colors.elevatedSurface
    }

    Surface(
        shape = RoundedCornerShape(WhislyTokens.RadiusCard),
        color = bubbleBg,
        border = BorderStroke(
            1.dp,
            if (turn.isPersonA) colors.border else WhislyTokens.SecondaryPeach
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("conversation_turn_bubble")
    ) {
        Column(
            modifier = Modifier.padding(WhislyTokens.Space16)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "${turn.speakerLabel} (${turn.sourceLangCode.uppercase()} → ${turn.targetLangCode.uppercase()})",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = colors.primaryAccent,
                        fontWeight = FontWeight.Bold
                    )
                )
                if (turn.ttsAvailable) {
                    IconButton(
                        onClick = onPlayAudio,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Play Translated Speech",
                            tint = colors.primaryAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    Text(
                        text = "Text Only",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = WhislyTokens.WarningText
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = turn.originalText,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = colors.mutedText
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = turn.translatedText,
                style = MaterialTheme.typography.titleMedium.copy(
                    color = colors.primaryCocoa,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}

@Preview(name = "Conversation Screen - Light", showBackground = true)
@Composable
private fun ConversationScreenLightPreview() {
    WhislyTheme(darkTheme = false) {
        ConversationScreen(
            uiState = TranslateUiState(
                conversationTurns = listOf(
                    ConversationTurn(
                        id = "1",
                        speakerLabel = "Person A",
                        isPersonA = true,
                        sourceLangCode = "ta",
                        targetLangCode = "bn",
                        originalText = "வணக்கம், நீங்கள் எப்படி இருக்கிறீர்கள்?",
                        translatedText = "নমস্কার, আপনি কেমন আছেন?",
                        timestamp = 1730000000000L,
                        confidence = 0.95f,
                        ttsAvailable = true
                    )
                )
            ),
            languages = IndicLanguageCatalog.buildInitialLanguageEntities().take(4),
            onSelectSource = {},
            onSelectTarget = {},
            onSwapLanguages = {},
            onSpeakerTurnAction = {},
            onSubmitTextTurn = { _, _ -> },
            onPlayTurnAudio = {},
            onClearConversation = {}
        )
    }
}

@Preview(name = "Conversation Screen - Dark", showBackground = true)
@Composable
private fun ConversationScreenDarkPreview() {
    WhislyTheme(darkTheme = true) {
        ConversationScreen(
            uiState = TranslateUiState(isDarkTheme = true),
            languages = IndicLanguageCatalog.buildInitialLanguageEntities().take(4),
            onSelectSource = {},
            onSelectTarget = {},
            onSwapLanguages = {},
            onSpeakerTurnAction = {},
            onSubmitTextTurn = { _, _ -> },
            onPlayTurnAudio = {},
            onClearConversation = {}
        )
    }
}
