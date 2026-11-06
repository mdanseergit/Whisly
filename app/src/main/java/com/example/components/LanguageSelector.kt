package com.example.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.theme.WhislyDesign
import com.example.core.theme.WhislyTokens
import com.example.data.local.LanguageEntity

@Composable
fun LanguageSelectorBar(
    sourceLanguage: LanguageEntity?,
    targetLanguage: LanguageEntity?,
    allLanguages: List<LanguageEntity>,
    onSelectSource: (String) -> Unit,
    onSelectTarget: (String) -> Unit,
    onSwapLanguages: () -> Unit,
    isSourceAutoDetect: Boolean = false,
    detectedLanguageName: String = "",
    modifier: Modifier = Modifier
) {
    val colors = WhislyDesign.colors
    var pickingSource by remember { mutableStateOf(false) }
    var pickingTarget by remember { mutableStateOf(false) }
    var swapRotationTarget by remember { mutableFloatStateOf(0f) }
    val animatedRotation by animateFloatAsState(
        targetValue = swapRotationTarget,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "swapRotation"
    )

    Surface(
        shape = RoundedCornerShape(WhislyTokens.RadiusCard),
        color = colors.cardSurface,
        border = BorderStroke(1.dp, colors.border),
        shadowElevation = 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = WhislyTokens.ScreenHorizontalPadding)
            .testTag("language_selector_bar")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            LanguageEndpointButton(
                roleLabel = "SPEAK IN",
                primaryTitle = if (isSourceAutoDetect) {
                    "Auto-detect"
                } else {
                    sourceLanguage?.nativeName ?: "தமிழ்"
                },
                secondarySubtitle = if (isSourceAutoDetect) {
                    if (detectedLanguageName.isNotBlank()) "Detected: $detectedLanguageName" else "Any supported language"
                } else {
                    sourceLanguage?.englishName ?: "Tamil"
                },
                onClick = { pickingSource = true },
                testTag = "source_language_selector",
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = {
                    swapRotationTarget += 180f
                    onSwapLanguages()
                },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(colors.elevatedSurface)
                    .testTag("swap_languages_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.SwapHoriz,
                    contentDescription = "Swap source and target languages",
                    tint = colors.primaryCocoa,
                    modifier = Modifier
                        .size(22.dp)
                        .rotate(animatedRotation)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            LanguageEndpointButton(
                roleLabel = "TRANSLATE TO",
                primaryTitle = targetLanguage?.nativeName ?: "বাংলা",
                secondarySubtitle = targetLanguage?.englishName ?: "Bengali",
                onClick = { pickingTarget = true },
                testTag = "target_language_selector",
                modifier = Modifier.weight(1f)
            )
        }
    }

    if (pickingSource) {
        LanguagePickerSheet(
            title = "Select Source Language",
            languages = allLanguages,
            selectedCode = if (isSourceAutoDetect) "auto" else (sourceLanguage?.code ?: "ta"),
            allowAutoDetect = true,
            onSelect = { code ->
                pickingSource = false
                onSelectSource(code)
            },
            onDismiss = { pickingSource = false }
        )
    }

    if (pickingTarget) {
        LanguagePickerSheet(
            title = "Select Target Language",
            languages = allLanguages,
            selectedCode = targetLanguage?.code ?: "bn",
            allowAutoDetect = false,
            onSelect = { code ->
                pickingTarget = false
                onSelectTarget(code)
            },
            onDismiss = { pickingTarget = false }
        )
    }
}

@Composable
private fun LanguageEndpointButton(
    roleLabel: String,
    primaryTitle: String,
    secondarySubtitle: String,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val colors = WhislyDesign.colors
    Surface(
        shape = RoundedCornerShape(WhislyTokens.RadiusControl),
        color = colors.background,
        border = BorderStroke(1.dp, colors.border),
        modifier = modifier
            .clip(RoundedCornerShape(WhislyTokens.RadiusControl))
            .clickable(onClick = onClick)
            .minimumInteractiveComponentSize()
            .testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = roleLabel,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = colors.mutedText,
                        fontWeight = FontWeight.Medium
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = primaryTitle,
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = colors.primaryCocoa,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = secondarySubtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = colors.mutedText
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Outlined.KeyboardArrowDown,
                contentDescription = "Choose language",
                tint = colors.primaryAccent,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguagePickerSheet(
    title: String,
    languages: List<LanguageEntity>,
    selectedCode: String,
    allowAutoDetect: Boolean,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = WhislyDesign.colors
    var searchQuery by remember { mutableStateOf("") }

    val filteredLanguages = remember(languages, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) {
            languages
        } else {
            languages.filter {
                it.englishName.lowercase().contains(q) ||
                    it.nativeName.lowercase().contains(q) ||
                    it.code.lowercase().contains(q)
            }
        }
    }

    val downloadedList = remember(filteredLanguages) { filteredLanguages.filter { it.isInstalled } }
    val popularList = remember(filteredLanguages) { filteredLanguages.filter { !it.isInstalled && it.isPopular } }
    val otherList = remember(filteredLanguages) { filteredLanguages.filter { !it.isInstalled && !it.isPopular } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(
            topStart = WhislyTokens.RadiusBottomSheet,
            topEnd = WhislyTokens.RadiusBottomSheet
        ),
        containerColor = colors.cardSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = WhislyTokens.ScreenHorizontalPadding, vertical = 12.dp)
                .testTag("language_picker_sheet")
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(
                    color = colors.primaryCocoa,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search languages...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        tint = colors.mutedText
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(WhislyTokens.RadiusButtonInput),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("picker_search_input")
            )
            Spacer(modifier = Modifier.height(12.dp))
            LazyColumn {
                if (allowAutoDetect && searchQuery.isBlank()) {
                    item(key = "auto_detect_option") {
                        val isAutoSelected = selectedCode == "auto"
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(WhislyTokens.RadiusControl))
                                .clickable { onSelect("auto") }
                                .padding(vertical = 12.dp, horizontal = 8.dp)
                                .testTag("picker_lang_auto")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    imageVector = Icons.Outlined.AutoAwesome,
                                    contentDescription = null,
                                    tint = colors.primaryAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Auto-detect",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            color = colors.primaryCocoa,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                    Text(
                                        text = "Detect spoken or typed language automatically",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = colors.mutedText
                                        )
                                    )
                                }
                            }
                            if (isAutoSelected) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = colors.primaryAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        HorizontalDivider(color = colors.border)
                    }
                }

                if (downloadedList.isNotEmpty()) {
                    item(key = "header_downloaded") {
                        PickerGroupHeader("Downloaded")
                    }
                    items(downloadedList, key = { "dl_${it.code}" }) { item ->
                        LanguagePickerRow(item = item, isSelected = item.code == selectedCode, onSelect = onSelect)
                    }
                }

                if (popularList.isNotEmpty()) {
                    item(key = "header_popular") {
                        PickerGroupHeader("Popular")
                    }
                    items(popularList, key = { "pop_${it.code}" }) { item ->
                        LanguagePickerRow(item = item, isSelected = item.code == selectedCode, onSelect = onSelect)
                    }
                }

                if (otherList.isNotEmpty()) {
                    item(key = "header_all") {
                        PickerGroupHeader("All")
                    }
                    items(otherList, key = { "all_${it.code}" }) { item ->
                        LanguagePickerRow(item = item, isSelected = item.code == selectedCode, onSelect = onSelect)
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PickerGroupHeader(title: String) {
    val colors = WhislyDesign.colors
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge.copy(
            color = colors.primaryAccent,
            fontWeight = FontWeight.Bold
        ),
        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp, start = 4.dp)
    )
}

@Composable
private fun LanguagePickerRow(
    item: LanguageEntity,
    isSelected: Boolean,
    onSelect: (String) -> Unit
) {
    val colors = WhislyDesign.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(WhislyTokens.RadiusControl))
            .clickable { onSelect(item.code) }
            .padding(vertical = 12.dp, horizontal = 8.dp)
            .testTag("picker_lang_${item.code}")
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.nativeName,
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = colors.primaryCocoa,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = item.englishName,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = colors.mutedText
                    )
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (item.isInstalled) "Downloaded" else "Download required",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = if (item.isInstalled) WhislyTokens.Success else colors.mutedText
                )
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (item.isInstalled) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Downloaded",
                    tint = if (isSelected) colors.primaryAccent else WhislyTokens.Success,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.CloudDownload,
                    contentDescription = "Download Required",
                    tint = colors.mutedText,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
    HorizontalDivider(color = colors.border)
}
