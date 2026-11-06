package com.example.presentation.home

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
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.components.LanguageSelectorBar
import com.example.components.StrandsGlassOrbCanvas
import com.example.components.WhislyTopBar
import com.example.core.theme.WhislyDesign
import com.example.core.theme.WhislyTokens
import com.example.data.local.LanguageEntity
import com.example.data.repositories.DecryptedHistoryItem
import com.example.presentation.TranslateUiState

/**
 * Home Screen with Deep Cocoa (#29211E) WhislyTopBar, Voice Translation Hero Card,
 * and clean Bold Title + Icon quick action tiles (Phase 4 & Phase 6).
 */
@Composable
fun HomeScreen(
    uiState: TranslateUiState,
    languages: List<LanguageEntity>,
    recentHistory: List<DecryptedHistoryItem>,
    isDarkTheme: Boolean,
    lowEndMode: Boolean,
    onToggleDarkTheme: () -> Unit,
    onSelectSource: (String) -> Unit,
    onSelectTarget: (String) -> Unit,
    onSwapLanguages: () -> Unit,
    onOpenTranslateVoice: () -> Unit,
    onOpenConversationMode: () -> Unit,
    onOpenTextMode: () -> Unit,
    onOpenLanguagesManager: () -> Unit,
    onOpenSavedPhrases: () -> Unit,
    onOpenHistory: () -> Unit,
    onSpeakHistoryItem: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = WhislyDesign.colors
    val sourceLang = languages.find { it.code == uiState.sourceLangCode }
    val targetLang = languages.find { it.code == uiState.targetLangCode }
    val isPairVerified = (sourceLang?.isInstalled == true) && (targetLang?.isInstalled == true)

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("home_screen")
    ) {
        WhislyTopBar(
            isOnline = uiState.isOnline,
            isPairVerifiedOffline = isPairVerified,
            isDarkTheme = isDarkTheme,
            onToggleTheme = onToggleDarkTheme,
            onStatusClick = onOpenLanguagesManager,
            themeToggleTestTag = "home_theme_toggle_button"
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = WhislyTokens.Space24)
        ) {
            Spacer(modifier = Modifier.height(WhislyTokens.Space16))

            // Hero Strands Voice Translation Card
            Surface(
                shape = RoundedCornerShape(WhislyTokens.RadiusMajorSurface),
                color = colors.cardSurface,
                border = BorderStroke(1.dp, colors.border),
                shadowElevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = WhislyTokens.ScreenHorizontalPadding)
                    .clickable(onClick = onOpenTranslateVoice)
                    .testTag("home_hero_voice_card")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(WhislyTokens.Space16)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Voice Translation",
                            style = MaterialTheme.typography.titleLarge.copy(
                                color = colors.primaryCocoa,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = Modifier.height(WhislyTokens.Space4))
                        Text(
                            text = "${sourceLang?.nativeName ?: "தமிழ்"} → ${targetLang?.nativeName ?: "বাংলা"}",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = colors.mutedText
                            )
                        )
                        Spacer(modifier = Modifier.height(WhislyTokens.Space12))
                        Surface(
                            shape = RoundedCornerShape(WhislyTokens.RadiusButtonInput),
                            color = WhislyTokens.PrimaryOrange,
                            modifier = Modifier.testTag("home_start_voice_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Mic,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Tap to Speak",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(WhislyTokens.Space12))

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(96.dp)
                    ) {
                        StrandsGlassOrbCanvas(
                            clock = 1.6f,
                            level = 0.52f,
                            pulse = 0f,
                            leanX = 0f,
                            leanY = 0f,
                            isLightMode = !isDarkTheme,
                            isRecording = false,
                            lowEndMode = lowEndMode,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(WhislyTokens.Space16))

            // Active Language Pair Selector
            LanguageSelectorBar(
                sourceLanguage = sourceLang,
                targetLanguage = targetLang,
                allLanguages = languages,
                onSelectSource = onSelectSource,
                onSelectTarget = onSelectTarget,
                onSwapLanguages = onSwapLanguages
            )

            Spacer(modifier = Modifier.height(WhislyTokens.Space16))

            // Quick Action Grid (2x2): Bold Title + Icon only (no redundant subtitles)
            Column(
                verticalArrangement = Arrangement.spacedBy(WhislyTokens.Space12),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = WhislyTokens.ScreenHorizontalPadding)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(WhislyTokens.Space12),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    QuickFeatureTile(
                        title = "Conversation",
                        icon = Icons.Outlined.Forum,
                        onClick = onOpenConversationMode,
                        testTag = "home_tile_conversation",
                        modifier = Modifier.weight(1f)
                    )
                    QuickFeatureTile(
                        title = "Text Mode",
                        icon = Icons.Outlined.Keyboard,
                        onClick = onOpenTextMode,
                        testTag = "home_tile_text_mode",
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(WhislyTokens.Space12),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    QuickFeatureTile(
                        title = "Language Packs",
                        icon = Icons.Outlined.Language,
                        onClick = onOpenLanguagesManager,
                        testTag = "home_tile_languages",
                        modifier = Modifier.weight(1f)
                    )
                    QuickFeatureTile(
                        title = "Saved Phrases",
                        icon = Icons.Outlined.BookmarkBorder,
                        onClick = onOpenSavedPhrases,
                        testTag = "home_tile_saved",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Recent Translations Section
            if (recentHistory.isNotEmpty()) {
                Spacer(modifier = Modifier.height(WhislyTokens.Space24))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = WhislyTokens.ScreenHorizontalPadding)
                ) {
                    Text(
                        text = "Recent Translations",
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = colors.primaryCocoa,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    TextButton(onClick = onOpenHistory) {
                        Text(
                            text = "View All",
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = WhislyTokens.PrimaryOrange,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(WhislyTokens.Space8),
                    modifier = Modifier.padding(horizontal = WhislyTokens.ScreenHorizontalPadding)
                ) {
                    recentHistory.take(3).forEach { item ->
                        Surface(
                            shape = RoundedCornerShape(WhislyTokens.RadiusCard),
                            color = colors.cardSurface,
                            border = BorderStroke(1.dp, colors.border),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.originalText,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = colors.mutedText
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = item.translatedText,
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            color = colors.primaryCocoa,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(
                                    onClick = { onSpeakHistoryItem(item.translatedText, item.targetLangCode) },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = "Play Translation",
                                        tint = WhislyTokens.PrimaryOrange
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickFeatureTile(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val colors = WhislyDesign.colors
    Surface(
        shape = RoundedCornerShape(WhislyTokens.RadiusCard),
        color = colors.cardSurface,
        border = BorderStroke(1.dp, colors.border),
        modifier = modifier
            .clip(RoundedCornerShape(WhislyTokens.RadiusCard))
            .clickable(onClick = onClick)
            .minimumInteractiveComponentSize()
            .testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(WhislyTokens.PrimaryOrange.copy(alpha = 0.12f))
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = WhislyTokens.PrimaryOrange,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    color = colors.primaryCocoa,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Home Screen - Light", showBackground = true)
@Composable
private fun HomeScreenLightPreview() {
    com.example.core.theme.WhislyTheme(darkTheme = false) {
        HomeScreen(
            uiState = TranslateUiState(isOnline = false),
            languages = com.example.ai.translation.IndicLanguageCatalog.buildInitialLanguageEntities().take(4),
            recentHistory = emptyList(),
            isDarkTheme = false,
            lowEndMode = false,
            onToggleDarkTheme = {},
            onSelectSource = {},
            onSelectTarget = {},
            onSwapLanguages = {},
            onOpenTranslateVoice = {},
            onOpenConversationMode = {},
            onOpenTextMode = {},
            onOpenLanguagesManager = {},
            onOpenSavedPhrases = {},
            onOpenHistory = {},
            onSpeakHistoryItem = { _, _ -> }
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Home Screen - Dark", showBackground = true)
@Composable
private fun HomeScreenDarkPreview() {
    com.example.core.theme.WhislyTheme(darkTheme = true) {
        HomeScreen(
            uiState = TranslateUiState(isOnline = true, isDarkTheme = true),
            languages = com.example.ai.translation.IndicLanguageCatalog.buildInitialLanguageEntities().take(4),
            recentHistory = emptyList(),
            isDarkTheme = true,
            lowEndMode = false,
            onToggleDarkTheme = {},
            onSelectSource = {},
            onSelectTarget = {},
            onSwapLanguages = {},
            onOpenTranslateVoice = {},
            onOpenConversationMode = {},
            onOpenTextMode = {},
            onOpenLanguagesManager = {},
            onOpenSavedPhrases = {},
            onOpenHistory = {},
            onSpeakHistoryItem = { _, _ -> }
        )
    }
}
