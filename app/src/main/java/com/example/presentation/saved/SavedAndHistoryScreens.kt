package com.example.presentation.saved

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.core.theme.WhislyDesign
import com.example.core.theme.WhislyTheme
import com.example.core.theme.WhislyTokens
import com.example.data.local.SavedPhraseEntity
import com.example.data.repositories.DecryptedHistoryItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SavedPhrasesScreen(
    phrases: List<SavedPhraseEntity>,
    searchQuery: String,
    selectedCategory: String,
    onSearchQueryChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onSpeakPhrase: (String, String) -> Unit,
    onDeletePhrase: (String) -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = WhislyDesign.colors
    val categories = listOf("All", "Greetings", "Travel", "Market", "Medical", "General")

    val filtered = phrases.filter { item ->
        val matchesCategory = selectedCategory == "All" || item.category.equals(selectedCategory, ignoreCase = true)
        val matchesQuery = searchQuery.isBlank() ||
            item.originalText.contains(searchQuery, ignoreCase = true) ||
            item.translatedText.contains(searchQuery, ignoreCase = true) ||
            item.phoneticHint.contains(searchQuery, ignoreCase = true)
        matchesCategory && matchesQuery
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = WhislyTokens.Space12)
            .testTag("saved_phrases_screen")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = WhislyTokens.ScreenHorizontalPadding)
        ) {
            Text(
                text = "Saved Phrases",
                style = MaterialTheme.typography.headlineMedium.copy(
                    color = colors.primaryCocoa,
                    fontWeight = FontWeight.Bold
                )
            )

            TextButton(
                onClick = onOpenHistory,
                modifier = Modifier.testTag("open_history_from_saved_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.History,
                    contentDescription = null,
                    tint = colors.primaryAccent,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "History",
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = colors.primaryAccent
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(WhislyTokens.Space12))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Search phrases…") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    tint = colors.mutedText
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(WhislyTokens.RadiusButtonInput),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.primaryAccent,
                unfocusedBorderColor = colors.border,
                focusedContainerColor = colors.cardSurface,
                unfocusedContainerColor = colors.cardSurface
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = WhislyTokens.ScreenHorizontalPadding)
                .testTag("saved_phrases_search_input")
        )

        Spacer(modifier = Modifier.height(WhislyTokens.Space12))

        LazyRow(
            contentPadding = PaddingValues(horizontal = WhislyTokens.ScreenHorizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(WhislyTokens.Space8)
        ) {
            items(categories) { category ->
                val isSelected = category == selectedCategory
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (isSelected) colors.primaryAccent else colors.cardSurface,
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) colors.primaryAccent else colors.border
                    ),
                    modifier = Modifier
                        .clickable { onCategoryChange(category) }
                        .testTag("saved_category_$category")
                ) {
                    Text(
                        text = category,
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = if (isSelected) {
                                WhislyTokens.WarmWhite
                            } else {
                                colors.primaryCocoa
                            },
                            fontWeight = FontWeight.SemiBold
                        ),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(WhislyTokens.Space12))

        if (filtered.isEmpty()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(WhislyTokens.Space32)
            ) {
                Icon(
                    imageVector = Icons.Outlined.BookmarkBorder,
                    contentDescription = null,
                    tint = colors.mutedText,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(WhislyTokens.Space8))
                Text(
                    text = "No saved phrases",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = colors.primaryCocoa,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    horizontal = WhislyTokens.ScreenHorizontalPadding,
                    vertical = WhislyTokens.Space8
                ),
                verticalArrangement = Arrangement.spacedBy(WhislyTokens.Space12),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filtered, key = { it.id }) { phrase ->
                    Surface(
                        shape = RoundedCornerShape(WhislyTokens.RadiusCard),
                        color = colors.cardSurface,
                        border = BorderStroke(1.dp, colors.border),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("saved_phrase_card_${phrase.id}")
                    ) {
                        Column(modifier = Modifier.padding(WhislyTokens.Space16)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(WhislyTokens.RadiusControl),
                                    color = colors.elevatedSurface
                                ) {
                                    Text(
                                        text = "${phrase.category} • ${phrase.sourceLangCode.uppercase()} → ${phrase.targetLangCode.uppercase()}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = colors.primaryAccent,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }

                                Row {
                                    IconButton(
                                        onClick = { onSpeakPhrase(phrase.translatedText, phrase.targetLangCode) },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("speak_saved_phrase_${phrase.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                            contentDescription = "Listen to saved translation",
                                            tint = colors.primaryAccent
                                        )
                                    }

                                    IconButton(
                                        onClick = { onDeletePhrase(phrase.id) },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("delete_saved_phrase_${phrase.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.DeleteOutline,
                                            contentDescription = "Delete Saved Phrase",
                                            tint = colors.mutedText
                                        )
                                    }
                                }
                            }

                            Text(
                                text = phrase.originalText,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = colors.mutedText
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = phrase.translatedText,
                                style = MaterialTheme.typography.titleMedium.copy(
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
}

@Composable
fun HistoryScreen(
    historyItems: List<DecryptedHistoryItem>,
    onSpeakItem: (String, String) -> Unit,
    onDeleteItem: (String) -> Unit,
    onClearAll: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
    val colors = WhislyDesign.colors
    val dateFormat = SimpleDateFormat("MMM d, hh:mm a", Locale.US)

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("history_screen")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = WhislyTokens.Space12, vertical = WhislyTokens.Space8)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("history_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = colors.primaryCocoa
                    )
                }
                Text(
                    text = "History",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        color = colors.primaryCocoa,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            if (historyItems.isNotEmpty()) {
                IconButton(
                    onClick = onClearAll,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("clear_all_history_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteSweep,
                        contentDescription = "Clear All History",
                        tint = WhislyTokens.Error
                    )
                }
            }
        }

        if (historyItems.isEmpty()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(WhislyTokens.Space32)
            ) {
                Icon(
                    imageVector = Icons.Outlined.History,
                    contentDescription = null,
                    tint = colors.mutedText,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(WhislyTokens.Space8))
                Text(
                    text = "No history yet",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = colors.primaryCocoa,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    horizontal = WhislyTokens.ScreenHorizontalPadding,
                    vertical = WhislyTokens.Space8
                ),
                verticalArrangement = Arrangement.spacedBy(WhislyTokens.Space12),
                modifier = Modifier.fillMaxSize()
            ) {
                items(historyItems, key = { it.id }) { item ->
                    Surface(
                        shape = RoundedCornerShape(WhislyTokens.RadiusCard),
                        color = colors.cardSurface,
                        border = BorderStroke(1.dp, colors.border),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("history_item_card")
                    ) {
                        Column(modifier = Modifier.padding(WhislyTokens.Space16)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "${item.sourceLangCode.uppercase()} → ${item.targetLangCode.uppercase()} • ${item.mode} • ${dateFormat.format(Date(item.createdAt))}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = colors.mutedText
                                    )
                                )
                                Row {
                                    IconButton(
                                        onClick = { onSpeakItem(item.translatedText, item.targetLangCode) },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                            contentDescription = "Play History Translation",
                                            tint = colors.primaryAccent,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { onDeleteItem(item.id) },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.DeleteOutline,
                                            contentDescription = "Delete History Item",
                                            tint = colors.mutedText,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = item.originalText,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = colors.mutedText
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = item.translatedText,
                                style = MaterialTheme.typography.titleMedium.copy(
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
}

@Preview(name = "Saved Phrases Screen - Light", showBackground = true)
@Composable
private fun SavedPhrasesScreenLightPreview() {
    WhislyTheme(darkTheme = false) {
        SavedPhrasesScreen(
            phrases = emptyList(),
            searchQuery = "",
            selectedCategory = "All",
            onSearchQueryChange = {},
            onCategoryChange = {},
            onSpeakPhrase = { _, _ -> },
            onDeletePhrase = {},
            onOpenHistory = {}
        )
    }
}

@Preview(name = "Saved Phrases Screen - Dark", showBackground = true)
@Composable
private fun SavedPhrasesScreenDarkPreview() {
    WhislyTheme(darkTheme = true) {
        SavedPhrasesScreen(
            phrases = emptyList(),
            searchQuery = "",
            selectedCategory = "All",
            onSearchQueryChange = {},
            onCategoryChange = {},
            onSpeakPhrase = { _, _ -> },
            onDeletePhrase = {},
            onOpenHistory = {}
        )
    }
}

@Preview(name = "History Screen - Light", showBackground = true)
@Composable
private fun HistoryScreenLightPreview() {
    WhislyTheme(darkTheme = false) {
        HistoryScreen(
            historyItems = listOf(
                DecryptedHistoryItem(
                    id = "1",
                    sourceLangCode = "ta",
                    targetLangCode = "bn",
                    originalText = "வணக்கம், நீங்கள் எப்படி இருக்கிறீர்கள்?",
                    translatedText = "নমস্কার, আপনি কেমন আছেন?",
                    mode = "VOICE",
                    sttConfidence = 0.94f,
                    inferenceLatencyMs = 42L,
                    createdAt = 1730000000000L
                )
            ),
            onSpeakItem = { _, _ -> },
            onDeleteItem = {},
            onClearAll = {},
            onBack = {}
        )
    }
}

@Preview(name = "History Screen - Dark", showBackground = true)
@Composable
private fun HistoryScreenDarkPreview() {
    WhislyTheme(darkTheme = true) {
        HistoryScreen(
            historyItems = listOf(
                DecryptedHistoryItem(
                    id = "1",
                    sourceLangCode = "ta",
                    targetLangCode = "bn",
                    originalText = "வணக்கம், நீங்கள் எப்படி இருக்கிறீர்கள்?",
                    translatedText = "নমস্কার, আপনি কেমন আছেন?",
                    mode = "VOICE",
                    sttConfidence = 0.94f,
                    inferenceLatencyMs = 42L,
                    createdAt = 1730000000000L
                )
            ),
            onSpeakItem = { _, _ -> },
            onDeleteItem = {},
            onClearAll = {},
            onBack = {}
        )
    }
}
