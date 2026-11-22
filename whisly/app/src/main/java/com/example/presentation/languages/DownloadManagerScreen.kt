package com.example.presentation.languages

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.unit.sp
import com.example.core.theme.WhislyDesign
import com.example.core.theme.WhislyTokens
import com.example.data.local.LanguageEntity
import com.example.domain.models.DownloadState
import java.util.Locale

/**
 * Phase 2 — Download Manager Screen.
 *
 * Provides:
 * - Active, Queued, Failed, and Completed sections.
 * - Real live progress, speed, and ETA.
 * - Pause, resume, cancel, retry controls.
 * - "Pause all" and Wi-Fi-only toggle.
 * - Storage summary and clear downloads with confirmation.
 */
@Composable
fun DownloadManagerScreen(
    languages: List<LanguageEntity>,
    downloadStates: Map<String, DownloadState>,
    usedStorageBytes: Long,
    freeStorageBytes: Long,
    downloadOverWifiOnly: Boolean,
    onToggleWifiOnly: (Boolean) -> Unit,
    onPauseDownload: (String) -> Unit,
    onResumeDownload: (String) -> Unit,
    onCancelDownload: (String) -> Unit,
    onRetryDownload: (String) -> Unit,
    onDeleteLanguage: (String) -> Unit,
    onPauseAll: () -> Unit,
    onClearAllDownloads: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = WhislyDesign.colors
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    val usedMb = usedStorageBytes / (1024.0 * 1024.0)
    val freeGb = freeStorageBytes / (1024.0 * 1024.0 * 1024.0)

    val activeItems = languages.filter {
        val s = downloadStates[it.code]
        s is DownloadState.Downloading || s is DownloadState.Verifying || it.installStatus == "DOWNLOADING" || it.installStatus == "VERIFYING"
    }
    val queuedItems = languages.filter {
        downloadStates[it.code] is DownloadState.Queued
    }
    val pausedItems = languages.filter {
        downloadStates[it.code] is DownloadState.Paused || it.installStatus == "PAUSED"
    }
    val failedItems = languages.filter {
        downloadStates[it.code] is DownloadState.Failed || it.installStatus == "FAILED" || it.installStatus == "CORRUPTED"
    }
    val completedItems = languages.filter { it.isInstalled }
    val availableItems = languages.filter {
        !it.isInstalled && it.code !in (activeItems + queuedItems + pausedItems + failedItems).map { item -> item.code }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .testTag("download_manager_screen")
    ) {
        // Pinned Header
        Surface(
            color = colors.glassNavSurface,
            border = BorderStroke(1.dp, colors.glassNavBorder),
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = WhislyTokens.Space8, vertical = WhislyTokens.Space12)
            ) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("dm_back_button")) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = colors.primaryCocoa
                    )
                }
                Text(
                    text = "Download Manager",
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = colors.primaryCocoa,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.weight(1f)
                )
                if (activeItems.isNotEmpty()) {
                    TextButton(onClick = onPauseAll, modifier = Modifier.testTag("pause_all_button")) {
                        Text("Pause all", color = colors.primaryAccent, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(WhislyTokens.Space16),
            verticalArrangement = Arrangement.spacedBy(WhislyTokens.Space16),
            modifier = Modifier.weight(1f)
        ) {
            // 1. Storage & Wi-Fi Settings Summary Card
            item {
                Card(
                    shape = RoundedCornerShape(WhislyTokens.RadiusCard),
                    colors = CardDefaults.cardColors(containerColor = colors.elevatedSurface),
                    border = BorderStroke(1.dp, colors.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(WhislyTokens.Space16)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Storage, contentDescription = null, tint = colors.primaryAccent)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Device Storage",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = colors.primaryCocoa,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = String.format(Locale.US, "Whisly offline models: %.1f MB  •  Free: %.1f GB", usedMb, freeGb),
                            style = MaterialTheme.typography.bodyMedium.copy(color = colors.mutedText)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Wifi, contentDescription = null, tint = colors.mutedText, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Download on Wi-Fi only", style = MaterialTheme.typography.bodyMedium.copy(color = colors.primaryCocoa))
                            }
                            Switch(
                                checked = downloadOverWifiOnly,
                                onCheckedChange = onToggleWifiOnly,
                                colors = SwitchDefaults.colors(checkedThumbColor = WhislyTokens.WarmWhite, checkedTrackColor = colors.primaryAccent)
                            )
                        }
                        if (completedItems.size > 2) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { showClearConfirmDialog = true },
                                shape = RoundedCornerShape(WhislyTokens.RadiusControl),
                                modifier = Modifier.fillMaxWidth().testTag("clear_all_downloads_button")
                            ) {
                                Icon(Icons.Outlined.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Clear extra downloads", color = colors.primaryCocoa)
                            }
                        }
                    }
                }
            }

            // 2. Active Downloads Section
            if (activeItems.isNotEmpty()) {
                item {
                    Text(
                        text = "Active Downloads (${activeItems.size})",
                        style = MaterialTheme.typography.titleSmall.copy(color = colors.primaryCocoa, fontWeight = FontWeight.Bold)
                    )
                }
                items(activeItems, key = { it.code }) { lang ->
                    val state = downloadStates[lang.code] ?: DownloadState.Downloading(0L, lang.totalSizeBytes)
                    DownloadItemCard(
                        language = lang,
                        state = state,
                        onPause = { onPauseDownload(lang.code) },
                        onResume = { onResumeDownload(lang.code) },
                        onCancel = { onCancelDownload(lang.code) },
                        onRetry = { onRetryDownload(lang.code) },
                        onDelete = { onDeleteLanguage(lang.code) }
                    )
                }
            }

            // 3. Queued & Paused Section
            if (queuedItems.isNotEmpty() || pausedItems.isNotEmpty()) {
                item {
                    Text(
                        text = "Queued & Paused (${queuedItems.size + pausedItems.size})",
                        style = MaterialTheme.typography.titleSmall.copy(color = colors.primaryCocoa, fontWeight = FontWeight.Bold)
                    )
                }
                items(queuedItems + pausedItems, key = { it.code }) { lang ->
                    val state = downloadStates[lang.code] ?: DownloadState.Paused
                    DownloadItemCard(
                        language = lang,
                        state = state,
                        onPause = { onPauseDownload(lang.code) },
                        onResume = { onResumeDownload(lang.code) },
                        onCancel = { onCancelDownload(lang.code) },
                        onRetry = { onRetryDownload(lang.code) },
                        onDelete = { onDeleteLanguage(lang.code) }
                    )
                }
            }

            // 4. Failed Section
            if (failedItems.isNotEmpty()) {
                item {
                    Text(
                        text = "Failed (${failedItems.size})",
                        style = MaterialTheme.typography.titleSmall.copy(color = WhislyTokens.Error, fontWeight = FontWeight.Bold)
                    )
                }
                items(failedItems, key = { it.code }) { lang ->
                    val state = downloadStates[lang.code] ?: DownloadState.Failed("Download failed")
                    DownloadItemCard(
                        language = lang,
                        state = state,
                        onPause = {},
                        onResume = {},
                        onCancel = { onCancelDownload(lang.code) },
                        onRetry = { onRetryDownload(lang.code) },
                        onDelete = { onDeleteLanguage(lang.code) }
                    )
                }
            }

            // 5. Completed Downloads
            if (completedItems.isNotEmpty()) {
                item {
                    Text(
                        text = "Downloaded Languages (${completedItems.size})",
                        style = MaterialTheme.typography.titleSmall.copy(color = colors.primaryCocoa, fontWeight = FontWeight.Bold)
                    )
                }
                items(completedItems, key = { it.code }) { lang ->
                    DownloadItemCard(
                        language = lang,
                        state = DownloadState.Installed,
                        onPause = {},
                        onResume = {},
                        onCancel = {},
                        onRetry = {},
                        onDelete = { onDeleteLanguage(lang.code) }
                    )
                }
            }

            // 6. Empty Active State Info
            if (activeItems.isEmpty() && queuedItems.isEmpty() && pausedItems.isEmpty() && failedItems.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(WhislyTokens.RadiusCard),
                        colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
                        border = BorderStroke(1.dp, colors.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(WhislyTokens.Space16)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CloudDownload,
                                contentDescription = null,
                                tint = colors.mutedText,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No Active Downloads",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = colors.primaryCocoa,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "All language packs are listed below. Tap Download to install any language for offline translation.",
                                style = MaterialTheme.typography.bodySmall.copy(color = colors.mutedText)
                            )
                        }
                    }
                }
            }

            // 7. Available Languages to Download
            if (availableItems.isNotEmpty()) {
                item {
                    Text(
                        text = "Available Language Packs (${availableItems.size})",
                        style = MaterialTheme.typography.titleSmall.copy(color = colors.primaryCocoa, fontWeight = FontWeight.Bold)
                    )
                }
                items(availableItems, key = { "avail_${it.code}" }) { lang ->
                    DownloadItemCard(
                        language = lang,
                        state = DownloadState.Idle,
                        onPause = {},
                        onResume = {},
                        onCancel = {},
                        onRetry = {},
                        onDelete = {},
                        onInstall = { onRetryDownload(lang.code) }
                    )
                }
            }
        }
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("Clear extra downloads?", fontWeight = FontWeight.Bold) },
            text = { Text("This will remove downloaded packs for optional languages to free up storage space. Default languages remain available.") },
            confirmButton = {
                Button(
                    onClick = {
                        showClearConfirmDialog = false
                        onClearAllDownloads()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WhislyTokens.Error)
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun DownloadItemCard(
    language: LanguageEntity,
    state: DownloadState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    onInstall: () -> Unit = {}
) {
    val colors = WhislyDesign.colors
    val sizeMb = String.format(Locale.US, "%.1f MB", language.totalSizeBytes / (1024.0 * 1024.0))

    Card(
        shape = RoundedCornerShape(WhislyTokens.RadiusCard),
        colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
        border = BorderStroke(1.dp, if (state is DownloadState.Installed) WhislyTokens.SecondaryPeach else colors.border),
        modifier = Modifier.fillMaxWidth().testTag("dm_item_${language.code}")
    ) {
        Column(modifier = Modifier.padding(WhislyTokens.Space16)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = language.nativeName,
                        style = MaterialTheme.typography.titleMedium.copy(color = colors.primaryCocoa, fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = language.englishName,
                        style = MaterialTheme.typography.bodySmall.copy(color = colors.mutedText)
                    )
                }

                // Actions
                when (state) {
                    is DownloadState.Downloading -> {
                        Row {
                            IconButton(onClick = onPause) {
                                Icon(Icons.Outlined.Pause, contentDescription = "Pause", tint = colors.primaryAccent)
                            }
                            IconButton(onClick = onCancel) {
                                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Cancel", tint = colors.mutedText)
                            }
                        }
                    }
                    is DownloadState.Paused -> {
                        Row {
                            IconButton(onClick = onResume) {
                                Icon(Icons.Outlined.PlayArrow, contentDescription = "Resume", tint = colors.primaryAccent)
                            }
                            IconButton(onClick = onCancel) {
                                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Cancel", tint = colors.mutedText)
                            }
                        }
                    }
                    is DownloadState.Failed -> {
                        Button(
                            onClick = onRetry,
                            shape = RoundedCornerShape(WhislyTokens.RadiusControl),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.primaryAccent)
                        ) {
                            Text("Retry")
                        }
                    }
                    is DownloadState.Installed -> {
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = "Remove", tint = colors.mutedText)
                        }
                    }
                    else -> {
                        Button(
                            onClick = onInstall,
                            shape = RoundedCornerShape(WhislyTokens.RadiusControl),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.primaryAccent)
                        ) {
                            Icon(Icons.Outlined.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Download")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Subtitle & Progress
            when (state) {
                is DownloadState.Downloading -> {
                    val pct = (state.progress * 100).toInt()
                    val speedStr = if (state.speedBytesPerSec > 0) {
                        String.format(Locale.US, "%.1f MB/s", state.speedBytesPerSec / (1024.0 * 1024.0))
                    } else "Calculating…"
                    val etaStr = if (state.etaSeconds > 0) "${state.etaSeconds}s left" else ""
                    Text(
                        text = "Downloading $pct% • $speedStr ${if (etaStr.isNotEmpty()) "• $etaStr" else ""}",
                        style = MaterialTheme.typography.labelSmall.copy(color = colors.primaryAccent)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { state.progress },
                        color = colors.primaryAccent,
                        trackColor = colors.elevatedSurface,
                        modifier = Modifier.fillMaxWidth().height(6.dp)
                    )
                }
                is DownloadState.Verifying -> {
                    val pct = (state.progress * 100).toInt()
                    Text(text = "Verifying integrity $pct%…", style = MaterialTheme.typography.labelSmall.copy(color = WhislyTokens.PrimaryOrange))
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { state.progress },
                        color = WhislyTokens.PrimaryOrange,
                        trackColor = colors.elevatedSurface,
                        modifier = Modifier.fillMaxWidth().height(6.dp)
                    )
                }
                is DownloadState.Queued -> {
                    Text(text = "Queued to download • $sizeMb", style = MaterialTheme.typography.labelSmall.copy(color = colors.mutedText))
                }
                is DownloadState.Paused -> {
                    Text(text = "Paused • $sizeMb", style = MaterialTheme.typography.labelSmall.copy(color = colors.mutedText))
                }
                is DownloadState.Failed -> {
                    Text(text = "Failed: ${state.reason}", style = MaterialTheme.typography.labelSmall.copy(color = WhislyTokens.Error))
                }
                is DownloadState.Installed -> {
                    Text(text = "Installed • $sizeMb • Offline ready", style = MaterialTheme.typography.labelSmall.copy(color = WhislyTokens.Success))
                }
                else -> {
                    Text(text = sizeMb, style = MaterialTheme.typography.labelSmall.copy(color = colors.mutedText))
                }
            }
        }
    }
}

