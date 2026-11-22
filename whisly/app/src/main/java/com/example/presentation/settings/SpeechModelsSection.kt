package com.example.presentation.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.theme.WhislyTokens
import com.example.presentation.ImportUiState

/**
 * Speech-model management.
 *
 * Models are never downloaded from a server. The user imports a `.zip` package that contains a
 * `whisly-stt.json` manifest, the model files, and a LICENSE. Every file is hash-checked before it
 * is installed, so a corrupted or tampered package is rejected instead of silently used.
 */
@Composable
fun SpeechModelsSection(
    installedPacks: List<String>,
    importState: ImportUiState,
    missingForSourceLabel: String?,
    onImportModel: (android.net.Uri) -> Unit,
    onDismissImport: () -> Unit,
    onRemoveModel: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = com.example.core.theme.WhislyDesign.colors
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onImportModel(uri)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Header("Speech Models")

        Surface(
            shape = RoundedCornerShape(WhislyTokens.RadiusCard),
            color = colors.cardSurface,
            border = BorderStroke(1.dp, colors.border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(WhislyTokens.Space16)) {
                if (missingForSourceLabel != null) {
                    Text(
                        text = "No speech model is installed for $missingForSourceLabel. " +
                            "Import a model package to speak, or type the text instead.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.mutedText
                    )
                    Spacer(Modifier.size(WhislyTokens.Space12))
                }

                when (importState) {
                    is ImportUiState.Importing -> {
                        Text(
                            text = if (importState.bytesTotal > 0) {
                                "Verifying package… ${formatMb(importState.bytesHashed)} of " +
                                    formatMb(importState.bytesTotal)
                            } else {
                                "Opening package…"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.primaryCocoa
                        )
                        Spacer(Modifier.size(WhislyTokens.Space8))
                        val progress = if (importState.bytesTotal > 0) {
                            (importState.bytesHashed.toFloat() / importState.bytesTotal)
                                .coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                        LinearProgressIndicator(
                            progress = { progress },
                            color = colors.primaryAccent,
                            trackColor = colors.border,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    is ImportUiState.Failed -> {
                        Text(
                            text = "Could not install that package: ${importState.reason}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = WhislyTokens.Error
                        )
                        Spacer(Modifier.size(WhislyTokens.Space8))
                        TextButton(
                            onClick = onDismissImport,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Text("Dismiss", color = colors.primaryAccent)
                        }
                    }

                    is ImportUiState.Installed -> {
                        Text(
                            text = "Installed ${importState.packId}" +
                                if (importState.languages.isNotEmpty()) {
                                    " for ${importState.languages.joinToString(", ")}."
                                } else {
                                    "."
                                },
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.primaryCocoa
                        )
                        Spacer(Modifier.size(WhislyTokens.Space8))
                        TextButton(
                            onClick = onDismissImport,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Text("Done", color = colors.primaryAccent)
                        }
                    }

                    ImportUiState.Idle -> Unit
                }

                if (installedPacks.isEmpty()) {
                    Text(
                        text = "No speech models installed",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.mutedText
                    )
                } else {
                    installedPacks.forEach { packId ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = WhislyTokens.Space4)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Memory,
                                contentDescription = null,
                                tint = colors.primaryAccent,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(WhislyTokens.Space12))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = packId,
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        color = colors.primaryCocoa,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                                Text(
                                    text = "Installed · verified",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.mutedText
                                )
                            }
                            IconButton(
                                onClick = { onRemoveModel(packId) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("remove_model_$packId")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.DeleteOutline,
                                    contentDescription = "Remove $packId",
                                    tint = colors.mutedText
                                )
                            }
                        }
                        HorizontalDivider(color = colors.border)
                    }
                }

                Spacer(Modifier.size(WhislyTokens.Space12))

                val busy = importState is ImportUiState.Importing
                Button(
                    onClick = { picker.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                    enabled = !busy,
                    shape = RoundedCornerShape(WhislyTokens.RadiusControl),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primaryAccent,
                        contentColor = colors.onPrimaryAccent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("import_speech_model_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Download,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(WhislyTokens.Space8))
                    Text("Import speech model (.zip)")
                }

                Spacer(Modifier.size(WhislyTokens.Space8))
                Text(
                    text = "Packages are verified on this device. Nothing is uploaded.",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.mutedText
                )
            }
        }
    }
}

@Composable
private fun Header(title: String) {
    val colors = com.example.core.theme.WhislyDesign.colors
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(
            color = colors.mutedText,
            fontWeight = FontWeight.SemiBold
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = WhislyTokens.Space4, bottom = WhislyTokens.Space8)
    )
}

private fun formatMb(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 100) "%.0f MB".format(mb) else "%.1f MB".format(mb)
}
