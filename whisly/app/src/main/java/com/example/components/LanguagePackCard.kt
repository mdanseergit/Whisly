package com.example.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.theme.WhislyDesign
import com.example.core.theme.WhislyTokens
import com.example.data.local.LanguageEntity
import com.example.domain.models.DownloadState
import java.util.Locale

/**
 * Phase 2 — Language Pack Row.
 *
 * Implements:
 * - Row with text in Column at weight(1f) and action at minimum size.
 * - Native name above English name with ellipsis and no mid-word wrapping.
 * - Clear state indication: color + icon + text.
 * - Determinate progress bar for Downloading and Verifying.
 */
@Composable
fun LanguagePackCard(
    language: LanguageEntity,
    downloadState: DownloadState = DownloadState.Idle,
    sharedMtInstalled: Boolean = true,
    onInstallClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onPauseClick: () -> Unit = {},
    onResumeClick: () -> Unit = {},
    onCancelClick: () -> Unit = onDeleteClick,
    modifier: Modifier = Modifier
) {
    val colors = WhislyDesign.colors
    val sizeMb = String.format(Locale.US, "%.1f MB", language.totalSizeBytes / (1024.0 * 1024.0))

    val effectiveState = when {
        language.isInstalled -> DownloadState.Installed
        downloadState !is DownloadState.Idle -> downloadState
        language.installStatus == "DOWNLOADING" -> DownloadState.Downloading(
            (language.downloadProgress * language.totalSizeBytes).toLong(),
            language.totalSizeBytes
        )
        language.installStatus == "VERIFYING" -> DownloadState.Verifying(
            (language.downloadProgress * language.totalSizeBytes).toLong(),
            language.totalSizeBytes
        )
        language.installStatus == "PAUSED" -> DownloadState.Paused
        language.installStatus == "FAILED" || language.installStatus == "CORRUPTED" -> DownloadState.Failed("Download failed")
        else -> DownloadState.Idle
    }

    val progressVal = when (effectiveState) {
        is DownloadState.Downloading -> effectiveState.progress
        is DownloadState.Verifying -> effectiveState.progress
        is DownloadState.Installed -> 1.0f
        else -> 0f
    }

    val animatedProgress by animateFloatAsState(
        targetValue = progressVal,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "packDownloadProgress_${language.code}"
    )

    Surface(
        shape = RoundedCornerShape(WhislyTokens.RadiusCard),
        color = colors.cardSurface,
        border = BorderStroke(
            width = 1.dp,
            color = if (language.isInstalled) WhislyTokens.SecondaryPeach else colors.border
        ),
        shadowElevation = 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("language_pack_card_${language.code}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = WhislyTokens.Space16, vertical = WhislyTokens.Space12)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Text Column with weight(1f)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Status Icon
                    when (effectiveState) {
                        is DownloadState.Installed -> Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = "Downloaded",
                            tint = WhislyTokens.Success,
                            modifier = Modifier.size(20.dp)
                        )
                        is DownloadState.Downloading -> Icon(
                            imageVector = Icons.Outlined.Download,
                            contentDescription = "Downloading",
                            tint = colors.primaryAccent,
                            modifier = Modifier.size(20.dp)
                        )
                        is DownloadState.Verifying -> Icon(
                            imageVector = Icons.Outlined.Schedule,
                            contentDescription = "Verifying",
                            tint = WhislyTokens.PrimaryOrange,
                            modifier = Modifier.size(20.dp)
                        )
                        is DownloadState.Queued -> Icon(
                            imageVector = Icons.Outlined.Schedule,
                            contentDescription = "Queued",
                            tint = colors.mutedText,
                            modifier = Modifier.size(20.dp)
                        )
                        is DownloadState.Paused -> Icon(
                            imageVector = Icons.Outlined.Pause,
                            contentDescription = "Paused",
                            tint = colors.mutedText,
                            modifier = Modifier.size(20.dp)
                        )
                        is DownloadState.Failed -> Icon(
                            imageVector = Icons.Outlined.ErrorOutline,
                            contentDescription = "Failed",
                            tint = WhislyTokens.Error,
                            modifier = Modifier.size(20.dp)
                        )
                        else -> Icon(
                            imageVector = Icons.Outlined.CloudDownload,
                            contentDescription = "Available",
                            tint = colors.mutedText,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(WhislyTokens.Space12))

                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .padding(end = WhislyTokens.Space8)
                    ) {
                        Text(
                            text = language.nativeName,
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = colors.primaryCocoa,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!language.englishName.equals(language.nativeName, ignoreCase = true)) {
                            Text(
                                text = language.englishName,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = colors.mutedText
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))

                        // Status Subtitle (Plain English text)
                        val statusText = when (effectiveState) {
                            is DownloadState.Installed -> "$sizeMb • Offline ready"
                            is DownloadState.Downloading -> {
                                if (effectiveState.indeterminate) {
                                    "Downloading…"
                                } else {
                                    val pct = (animatedProgress * 100).toInt()
                                    val speedText = if (effectiveState.speedBytesPerSec > 0) {
                                        String.format(Locale.US, " • %.1f MB/s", effectiveState.speedBytesPerSec / (1024.0 * 1024.0))
                                    } else ""
                                    "Downloading $pct%$speedText"
                                }
                            }
                            is DownloadState.Verifying -> "Verifying ${(animatedProgress * 100).toInt()}%"
                            is DownloadState.Installing -> "Installing…"
                            is DownloadState.Queued -> "Queued"
                            is DownloadState.Paused -> "Paused"
                            is DownloadState.Failed -> "Failed: ${effectiveState.reason}"
                            else -> sizeMb
                        }
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = if (effectiveState is DownloadState.Failed) WhislyTokens.Error else colors.mutedText,
                                fontSize = 12.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Action Button at minimum size
                when (effectiveState) {
                    is DownloadState.Installed -> {
                        IconButton(
                            onClick = onDeleteClick,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("delete_pack_${language.code}")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = "Remove ${language.englishName} Pack",
                                tint = colors.mutedText
                            )
                        }
                    }
                    is DownloadState.Downloading -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = onPauseClick,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("pause_pack_${language.code}")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Pause,
                                    contentDescription = "Pause",
                                    tint = colors.primaryAccent
                                )
                            }
                        }
                    }
                    is DownloadState.Paused -> {
                        Button(
                            onClick = onResumeClick,
                            shape = RoundedCornerShape(WhislyTokens.RadiusControl),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.primaryAccent),
                            modifier = Modifier
                                .height(44.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("resume_pack_${language.code}")
                        ) {
                            Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Resume")
                        }
                    }
                    is DownloadState.Failed -> {
                        OutlinedButton(
                            onClick = onInstallClick,
                            shape = RoundedCornerShape(WhislyTokens.RadiusControl),
                            modifier = Modifier
                                .height(44.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("retry_pack_${language.code}")
                        ) {
                            Text("Retry", color = colors.primaryCocoa)
                        }
                    }
                    is DownloadState.Verifying, is DownloadState.Installing, is DownloadState.Queued -> {
                        Button(
                            onClick = {},
                            enabled = false,
                            shape = RoundedCornerShape(WhislyTokens.RadiusControl),
                            modifier = Modifier
                                .height(44.dp)
                                .minimumInteractiveComponentSize()
                        ) {
                            Text(if (effectiveState is DownloadState.Queued) "Queued" else "Verifying…")
                        }
                    }
                    else -> {
                        Button(
                            onClick = onInstallClick,
                            shape = RoundedCornerShape(WhislyTokens.RadiusControl),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.primaryAccent,
                                contentColor = WhislyTokens.WarmWhite
                            ),
                            modifier = Modifier
                                .height(44.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("install_pack_${language.code}")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Download,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Download",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    color = WhislyTokens.WarmWhite,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                    }
                }
            }

            // Progress bar for live downloads and verification
            val isIndeterminateDownload = effectiveState is DownloadState.Downloading && (effectiveState as DownloadState.Downloading).indeterminate
            if (effectiveState is DownloadState.Downloading || effectiveState is DownloadState.Verifying) {
                Spacer(modifier = Modifier.height(WhislyTokens.Space8))
                if (isIndeterminateDownload) {
                    LinearProgressIndicator(
                        color = colors.primaryAccent,
                        trackColor = colors.elevatedSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        color = if (effectiveState is DownloadState.Verifying) WhislyTokens.PrimaryOrange else colors.primaryAccent,
                        trackColor = colors.elevatedSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                    )
                }
            }
        }
    }
}
