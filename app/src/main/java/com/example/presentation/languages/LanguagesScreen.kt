package com.example.presentation.languages

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.ai.translation.IndicLanguageCatalog
import com.example.components.LanguagePackCard
import com.example.core.theme.WhislyDesign
import com.example.core.theme.WhislyTheme
import com.example.core.theme.WhislyTokens
import com.example.data.local.InstalledModelEntity
import com.example.data.local.LanguageEntity
import java.util.Locale

@Composable
fun LanguagesScreen(
    languages: List<LanguageEntity>,
    installedModels: List<InstalledModelEntity>,
    availableStorageBytes: Long,
    statusBanner: String?,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    onInstallLanguage: (String) -> Unit,
    onRemoveLanguage: (String) -> Unit,
    onClearAllExtraPacks: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = WhislyDesign.colors
    val sharedMtInstalled = installedModels.any {
        it.packId == IndicLanguageCatalog.SHARED_MT_PACK_ID && it.isVerified
    }
    val installedLangCount = languages.count { it.isInstalled }
    val extraInstalledCount = languages.count { it.isInstalled && it.code !in setOf("ta", "bn", "en") }
    val freeStorageGb = availableStorageBytes / (1024.0 * 1024.0 * 1024.0)

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

    val downloadedLanguages = remember(filteredLanguages) {
        filteredLanguages.filter { it.isInstalled }
    }
    val popularLanguages = remember(filteredLanguages) {
        filteredLanguages.filter { !it.isInstalled && it.isPopular }
    }
    val allOtherLanguages = remember(filteredLanguages) {
        filteredLanguages.filter { !it.isInstalled && !it.isPopular }
    }

    LazyColumn(
        contentPadding = PaddingValues(
            horizontal = WhislyTokens.ScreenHorizontalPadding,
            vertical = WhislyTokens.Space16
        ),
        verticalArrangement = Arrangement.spacedBy(WhislyTokens.Space12),
        modifier = modifier
            .fillMaxSize()
            .testTag("languages_screen")
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = "Languages",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            color = colors.primaryCocoa,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Storage,
                            contentDescription = null,
                            tint = colors.mutedText,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = String.format(
                                Locale.US,
                                "%d downloaded • %.1f GB free",
                                installedLangCount,
                                freeStorageGb
                            ),
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = colors.mutedText
                            )
                        )
                    }
                }
                if (extraInstalledCount > 0) {
                    Spacer(modifier = Modifier.width(WhislyTokens.Space8))
                    OutlinedButton(
                        onClick = onClearAllExtraPacks,
                        shape = RoundedCornerShape(WhislyTokens.RadiusControl),
                        border = BorderStroke(1.dp, WhislyTokens.SecondaryPeach),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.primaryCocoa),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("clear_extra_packs_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteSweep,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear Extra", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
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
                    .testTag("language_search_input")
            )
        }

        if (!statusBanner.isNullOrBlank()) {
            item {
                Surface(
                    shape = RoundedCornerShape(WhislyTokens.RadiusButtonInput),
                    color = colors.elevatedSurface,
                    border = BorderStroke(1.dp, WhislyTokens.SecondaryPeach)
                ) {
                    Text(
                        text = statusBanner,
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = colors.primaryCocoa,
                            fontWeight = FontWeight.SemiBold
                        ),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }
        }

        if (downloadedLanguages.isNotEmpty()) {
            item(key = "section_downloaded") {
                LanguageSectionHeader("Downloaded")
            }
            items(downloadedLanguages, key = { "dl_${it.code}" }) { lang ->
                LanguagePackCard(
                    language = lang,
                    sharedMtInstalled = sharedMtInstalled,
                    onInstallClick = { onInstallLanguage(lang.code) },
                    onDeleteClick = { onRemoveLanguage(lang.code) }
                )
            }
        }

        if (popularLanguages.isNotEmpty()) {
            item(key = "section_popular") {
                LanguageSectionHeader("Popular")
            }
            items(popularLanguages, key = { "pop_${it.code}" }) { lang ->
                LanguagePackCard(
                    language = lang,
                    sharedMtInstalled = sharedMtInstalled,
                    onInstallClick = { onInstallLanguage(lang.code) },
                    onDeleteClick = { onRemoveLanguage(lang.code) }
                )
            }
        }

        if (allOtherLanguages.isNotEmpty()) {
            item(key = "section_all") {
                LanguageSectionHeader("All")
            }
            items(allOtherLanguages, key = { "all_${it.code}" }) { lang ->
                LanguagePackCard(
                    language = lang,
                    sharedMtInstalled = sharedMtInstalled,
                    onInstallClick = { onInstallLanguage(lang.code) },
                    onDeleteClick = { onRemoveLanguage(lang.code) }
                )
            }
        }
    }
}

@Composable
private fun LanguageSectionHeader(title: String) {
    val colors = WhislyDesign.colors
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall.copy(
            color = colors.primaryAccent,
            fontWeight = FontWeight.Bold
        ),
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Preview(name = "Languages Screen - Light", showBackground = true)
@Composable
private fun LanguagesScreenLightPreview() {
    WhislyTheme(darkTheme = false) {
        LanguagesScreen(
            languages = IndicLanguageCatalog.buildInitialLanguageEntities().take(4),
            installedModels = emptyList(),
            availableStorageBytes = 12L * 1024 * 1024 * 1024,
            statusBanner = null,
            onInstallLanguage = {},
            onRemoveLanguage = {}
        )
    }
}

@Preview(name = "Languages Screen - Dark", showBackground = true)
@Composable
private fun LanguagesScreenDarkPreview() {
    WhislyTheme(darkTheme = true) {
        LanguagesScreen(
            languages = IndicLanguageCatalog.buildInitialLanguageEntities().take(4),
            installedModels = emptyList(),
            availableStorageBytes = 12L * 1024 * 1024 * 1024,
            statusBanner = "Pack ready",
            onInstallLanguage = {},
            onRemoveLanguage = {}
        )
    }
}
