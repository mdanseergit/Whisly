package com.example.presentation.safemode

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.crash.CrashProtectionManager
import com.example.core.theme.WhislyDesign
import com.example.core.theme.WhislyTokens

/**
 * Minimal, Apple-style Safe Mode Screen displayed after 2 consecutive launch crashes.
 *
 * Implements Phase 2 specifications:
 * - "Whisly encountered repeated launch failures."
 * - [Retry Normal Start]
 * - [Clear Downloaded Model Packs (keeps user history)]
 * - [Export Crash Report (copies log to clipboard / shares txt file)]
 */
@Composable
fun SafeModeScreen(
    onRetryNormalStart: () -> Unit,
    onRestartAfterClearing: () -> Unit
) {
    val context = LocalContext.current
    var isClearing by remember { mutableStateOf(false) }
    var showReportDetails by remember { mutableStateOf(false) }
    var showExitReportView by remember { mutableStateOf(false) }
    val crashReport = remember { CrashProtectionManager.getCrashReport(context) }

    if (showExitReportView) {
        ExitReportScreen(onBack = { showExitReportView = false })
        return
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("safe_mode_screen"),
        color = WhislyTokens.LightBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Shield / Warning Badge
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFFF3E0)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.WarningAmber,
                    contentDescription = "Safe Mode Warning",
                    tint = Color(0xFFE65100),
                    modifier = Modifier.size(38.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Safe Mode",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = WhislyTokens.DeepBrown,
                fontFamily = com.example.core.theme.InterFontFamily
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Whisly encountered repeated launch failures.",
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
                color = WhislyTokens.LightSecondaryText,
                textAlign = TextAlign.Center,
                fontFamily = com.example.core.theme.InterFontFamily
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "To protect your saved translations and preferences, Whisly started in diagnostic recovery mode.",
                fontSize = 13.sp,
                color = WhislyTokens.LightSecondaryText,
                textAlign = TextAlign.Center,
                fontFamily = com.example.core.theme.InterFontFamily,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Action 1: Retry Normal Start
            Button(
                onClick = {
                    CrashProtectionManager.resetCrashLoop(context)
                    onRetryNormalStart()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("safe_mode_retry_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = WhislyTokens.PrimaryOrange,
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Retry Normal Start",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action 2: Clear Downloaded Model Packs
            OutlinedButton(
                onClick = {
                    if (!isClearing) {
                        isClearing = true
                        CrashProtectionManager.clearDownloadedModelPacks(context) {
                            Toast.makeText(
                                context,
                                "Model packs cleared. Preserved user history.",
                                Toast.LENGTH_SHORT
                            ).show()
                            onRestartAfterClearing()
                        }
                    }
                },
                enabled = !isClearing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("safe_mode_clear_packs_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = WhislyTokens.DeepBrown
                )
            ) {
                Icon(
                    imageVector = Icons.Default.CleaningServices,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = WhislyTokens.DeepBrown
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isClearing) "Clearing Model Packs..." else "Clear Downloaded Model Packs",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action 3: Export Crash Report
            OutlinedButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    clipboard?.setPrimaryClip(ClipData.newPlainText("Whisly Crash Report", crashReport))

                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "Whisly Launch Crash Diagnosis")
                        putExtra(Intent.EXTRA_TEXT, crashReport)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Export Crash Report"))
                    Toast.makeText(context, "Crash report copied to clipboard", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("safe_mode_export_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = WhislyTokens.DeepBrown
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = WhislyTokens.DeepBrown
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Export Crash Report",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action 4: View Application Exit Diagnostics (ApplicationExitInfo)
            OutlinedButton(
                onClick = { showExitReportView = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("safe_mode_view_exit_report_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = WhislyTokens.DeepBrown
                )
            ) {
                Icon(
                    imageVector = Icons.Default.BugReport,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = WhislyTokens.DeepBrown
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "View Exit Diagnostics",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Expandable Diagnostic Log Accordion
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showReportDetails = !showReportDetails }
                    .border(1.dp, WhislyTokens.LightBorder, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF9F5F1))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.BugReport,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = WhislyTokens.LightSecondaryText
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Crash Diagnostic Details",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = WhislyTokens.DeepBrown
                            )
                        }
                        Icon(
                            imageVector = if (showReportDetails) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = WhislyTokens.LightSecondaryText
                        )
                    }

                    AnimatedVisibility(visible = showReportDetails) {
                        Column {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = crashReport,
                                fontSize = 11.sp,
                                color = WhislyTokens.DeepBrown,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 15.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.White, RoundedCornerShape(6.dp))
                                    .padding(8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
