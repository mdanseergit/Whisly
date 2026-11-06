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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.theme.WhislyDesign
import com.example.core.theme.WhislyTokens
import com.example.data.local.LanguageEntity
import java.util.Locale

/**
 * PHASE 4 — Clean Language Pack Row (`LanguagePackCard`).
 *
 * Shows only:
 * 1. Language name (Native + English)
 * 2. Pack size (`sizeMb`)
 * 3. Status icon (`CheckCircle` / `CloudDownload`)
 * 4. One action (`Download` button or `Delete` icon button)
 */
@Composable
fun LanguagePackCard(
    language: LanguageEntity,
    sharedMtInstalled: Boolean,
    onInstallClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = WhislyDesign.colors
    val animatedProgress by animateFloatAsState(
        targetValue = language.downloadProgress,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "packDownloadProgress"
    )
    val sizeMb = String.format(Locale.US, "%.1f MB", language.totalSizeBytes / (1024.0 * 1024.0))
    val isDownloading = language.installStatus == "DOWNLOADING"

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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (language.isInstalled) {
                            Icons.Outlined.CheckCircle
                        } else {
                            Icons.Outlined.CloudDownload
                        },
                        contentDescription = if (language.isInstalled) "Installed" else "Not downloaded",
                        tint = if (language.isInstalled) WhislyTokens.Success else colors.mutedText,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(WhislyTokens.Space12))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = language.nativeName,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = colors.primaryCocoa,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Spacer(modifier = Modifier.width(WhislyTokens.Space8))
                            Text(
                                text = language.englishName,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = colors.mutedText
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = sizeMb,
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = colors.mutedText
                            )
                        )
                    }
                }

                if (language.isInstalled) {
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
                } else {
                    Button(
                        onClick = onInstallClick,
                        enabled = !isDownloading,
                        shape = RoundedCornerShape(WhislyTokens.RadiusControl),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primaryAccent,
                            contentColor = WhislyTokens.WarmWhite
                        ),
                        modifier = Modifier
                            .height(48.dp)
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
                            text = if (isDownloading) "${(animatedProgress * 100).toInt()}%" else "Download",
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = WhislyTokens.WarmWhite,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }
            }

            if (isDownloading) {
                Spacer(modifier = Modifier.height(WhislyTokens.Space8))
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    color = colors.primaryAccent,
                    trackColor = colors.elevatedSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                )
            }
        }
    }
}
