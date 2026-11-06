package com.example.presentation.about

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.VideogameAsset
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.R
import com.example.components.WhislyLogo
import com.example.core.config.AppConfig
import com.example.core.theme.WhislyDesign
import com.example.core.theme.WhislyTheme
import com.example.core.theme.WhislyTokens
import java.util.Calendar

/**
 * Opens an external URL using Chrome Custom Tabs intent extras with automatic
 * fallback to standard `Intent.ACTION_VIEW` and safe `ActivityNotFoundException` handling.
 */
fun openExternalUrlSafely(
    context: Context,
    url: String,
    onUrlLaunched: ((String) -> Unit)? = null
): Boolean {
    if (url.isBlank()) return false
    onUrlLaunched?.invoke(url)
    return try {
        val uri = Uri.parse(url)
        val customTabsIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            val extras = Bundle()
            extras.putBinder("android.support.customtabs.extra.SESSION", null)
            putExtras(extras)
            putExtra("android.support.customtabs.extra.TITLE_VISIBILITY", 1)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(customTabsIntent)
        true
    } catch (_: ActivityNotFoundException) {
        try {
            val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallbackIntent)
            true
        } catch (_: Exception) {
            false
        }
    } catch (_: Exception) {
        false
    }
}

/**
 * PHASE 5 — About Whisly Screen (`AboutWhislyScreen.kt`).
 *
 * Reachable from Profile, bottom nav hidden, back button.
 * Implements Sections A through E with exact copy, Custom Tabs / ACTION_VIEW fallback,
 * neutral wishlist vector chips, conditional "Support the Dream" button, and dynamic footer.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AboutWhislyScreen(
    onBack: () -> Unit,
    supportUrlOverride: String = AppConfig.SUPPORT_URL,
    onExternalUrlClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val colors = WhislyDesign.colors
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("about_whisly_screen")
    ) {
        // Sticky Top Bar with Back Button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = WhislyTokens.Space12, vertical = WhislyTokens.Space8)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("about_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = colors.primaryCocoa
                )
            }
            Text(
                text = "About Whisly",
                style = MaterialTheme.typography.titleLarge.copy(
                    color = colors.primaryCocoa,
                    fontWeight = FontWeight.Bold
                )
            )
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(WhislyTokens.Space16),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = WhislyTokens.ScreenHorizontalPadding,
                    end = WhislyTokens.ScreenHorizontalPadding,
                    bottom = WhislyTokens.Space32
                )
        ) {
            // Section A — Header: WhislyLogo, bold "WHISLY", version, small "Made with care"
            Surface(
                shape = RoundedCornerShape(WhislyTokens.RadiusMajorSurface),
                color = colors.cardSurface,
                border = BorderStroke(1.dp, colors.border),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("about_header_card")
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = WhislyTokens.Space24, horizontal = WhislyTokens.Space16)
                ) {
                    WhislyLogo(
                        size = 72.dp,
                        cornerRadius = 18.dp
                    )
                    Spacer(modifier = Modifier.height(WhislyTokens.Space12))
                    Text(
                        text = "WHISLY",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            color = colors.primaryCocoa,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.0.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(WhislyTokens.Space4))
                    Text(
                        text = "v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = colors.mutedText
                        )
                    )
                    Spacer(modifier = Modifier.height(WhislyTokens.Space4))
                    Text(
                        text = "Made with care",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = WhislyTokens.PrimaryOrange
                        )
                    )
                }
            }

            // Section B — "About Whisly" (Bold)
            Surface(
                shape = RoundedCornerShape(WhislyTokens.RadiusCard),
                color = colors.cardSurface,
                border = BorderStroke(1.dp, colors.border),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("about_mission_card")
            ) {
                Column(modifier = Modifier.padding(WhislyTokens.Space16)) {
                    Text(
                        text = "About Whisly",
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = colors.primaryCocoa,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = Modifier.height(WhislyTokens.Space8))
                    Text(
                        text = "Whisly is an offline-first voice translation application designed to make communication across languages simple, natural and accessible.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = colors.primaryCocoa
                        )
                    )
                    Spacer(modifier = Modifier.height(WhislyTokens.Space8))
                    Text(
                        text = "Speak in your language. Understand another. Anywhere.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = colors.mutedText
                        )
                    )
                }
            }

            // Section C — "Meet the Creator" (Bold)
            Surface(
                shape = RoundedCornerShape(WhislyTokens.RadiusCard),
                color = colors.cardSurface,
                border = BorderStroke(1.dp, colors.border),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("about_creator_card")
            ) {
                Column(modifier = Modifier.padding(WhislyTokens.Space16)) {
                    Text(
                        text = "Meet the Creator",
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = colors.primaryCocoa,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = Modifier.height(WhislyTokens.Space8))
                    Text(
                        text = AppConfig.CREATOR_NAME,
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = colors.primaryCocoa,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.testTag("about_creator_name")
                    )
                    Spacer(modifier = Modifier.height(WhislyTokens.Space4))
                    Text(
                        text = "Independent App Developer & Creator of Whisly.",
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = WhislyTokens.PrimaryOrange
                        )
                    )
                    Spacer(modifier = Modifier.height(WhislyTokens.Space8))
                    Text(
                        text = "Building useful technology at the intersection of AI, software engineering and creative ideas.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = colors.mutedText
                        )
                    )
                    Spacer(modifier = Modifier.height(WhislyTokens.Space16))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(WhislyTokens.Space12),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = {
                                openExternalUrlSafely(
                                    context = context,
                                    url = AppConfig.PORTFOLIO_URL,
                                    onUrlLaunched = onExternalUrlClick
                                )
                            },
                            shape = RoundedCornerShape(WhislyTokens.RadiusButtonInput),
                            border = BorderStroke(1.dp, colors.border),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = colors.background,
                                contentColor = colors.primaryCocoa
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("about_portfolio_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Language,
                                contentDescription = null,
                                tint = WhislyTokens.PrimaryOrange,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "My Portfolio",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    color = colors.primaryCocoa,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                openExternalUrlSafely(
                                    context = context,
                                    url = AppConfig.GITHUB_URL,
                                    onUrlLaunched = onExternalUrlClick
                                )
                            },
                            shape = RoundedCornerShape(WhislyTokens.RadiusButtonInput),
                            border = BorderStroke(1.dp, colors.border),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = colors.background,
                                contentColor = colors.primaryCocoa
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("about_github_button")
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_github),
                                contentDescription = null,
                                tint = colors.primaryCocoa,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "GitHub",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    color = colors.primaryCocoa,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                    }
                }
            }

            // Section D — "A Little Dream"
            Surface(
                shape = RoundedCornerShape(WhislyTokens.RadiusCard),
                color = colors.cardSurface,
                border = BorderStroke(1.dp, colors.border),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("about_dream_card")
            ) {
                Column(modifier = Modifier.padding(WhislyTokens.Space16)) {
                    Text(
                        text = "A Little Dream",
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = colors.primaryCocoa,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = Modifier.height(WhislyTokens.Space8))
                    Text(
                        text = "Building Whisly is a passion project. One day, I'd love to celebrate it with a PS5 and GTA VI.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = colors.primaryCocoa
                        )
                    )
                    Spacer(modifier = Modifier.height(WhislyTokens.Space12))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(WhislyTokens.Space8),
                        verticalArrangement = Arrangement.spacedBy(WhislyTokens.Space8),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        WishlistChip(
                            label = "PlayStation 5",
                            icon = Icons.Outlined.VideogameAsset,
                            testTag = "wishlist_chip_ps5"
                        )
                        WishlistChip(
                            label = "Grand Theft Auto VI",
                            icon = Icons.Outlined.SportsEsports,
                            testTag = "wishlist_chip_gta6"
                        )
                    }

                    Spacer(modifier = Modifier.height(WhislyTokens.Space12))
                    Text(
                        text = "Maybe one day, with your support. Until then, back to building.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = colors.mutedText
                        )
                    )

                    if (supportUrlOverride.isNotBlank()) {
                        Spacer(modifier = Modifier.height(WhislyTokens.Space12))
                        Button(
                            onClick = {
                                openExternalUrlSafely(
                                    context = context,
                                    url = supportUrlOverride,
                                    onUrlLaunched = onExternalUrlClick
                                )
                            },
                            shape = RoundedCornerShape(WhislyTokens.RadiusButtonInput),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = WhislyTokens.PrimaryOrange,
                                contentColor = WhislyTokens.WarmWhite
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("about_support_dream_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FavoriteBorder,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Support the Dream",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    color = WhislyTokens.WarmWhite,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(WhislyTokens.Space12))
                    Text(
                        text = "Personal wishlist, not affiliated with or endorsed by Sony, Rockstar Games or Take-Two Interactive.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = colors.mutedText,
                            fontSize = 12.sp
                        ),
                        modifier = Modifier.testTag("about_dream_disclaimer")
                    )
                }
            }

            // Section E — Footer (muted): Whisly, Creator, real versionName, real versionCode, copyright year
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = WhislyTokens.Space12)
                    .testTag("about_footer")
            ) {
                Text(
                    text = "Whisly • Created by ${AppConfig.CREATOR_NAME}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = colors.mutedText
                    ),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(WhislyTokens.Space4))
                Text(
                    text = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) • © $currentYear ${AppConfig.CREATOR_NAME}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = colors.mutedText,
                        fontSize = 12.sp
                    ),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun WishlistChip(
    label: String,
    icon: ImageVector,
    testTag: String
) {
    val colors = WhislyDesign.colors
    Surface(
        shape = RoundedCornerShape(50),
        color = colors.elevatedSurface,
        border = BorderStroke(1.dp, colors.border),
        modifier = Modifier.testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = WhislyTokens.PrimaryOrange,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    color = colors.primaryCocoa,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }
}

@Preview(name = "About Whisly - Light", showBackground = true)
@Composable
private fun AboutWhislyLightPreview() {
    WhislyTheme(darkTheme = false) {
        AboutWhislyScreen(onBack = {})
    }
}

@Preview(name = "About Whisly - Dark", showBackground = true)
@Composable
private fun AboutWhislyDarkPreview() {
    WhislyTheme(darkTheme = true) {
        AboutWhislyScreen(onBack = {})
    }
}
