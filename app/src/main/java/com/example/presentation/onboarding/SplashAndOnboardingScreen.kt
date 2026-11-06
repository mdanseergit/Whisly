package com.example.presentation.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.OfflineBolt
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.components.WhislyLogoMark
import com.example.components.WhislyPrimaryButton
import com.example.core.theme.WhislyDesign
import com.example.core.theme.WhislyTokens
import kotlinx.coroutines.delay

/**
 * Splash & 3-Page Onboarding Screen (Section 12).
 *
 * 1. Speak in your language
 * 2. Whisly translates your words
 * 3. Hear the translation offline
 */
@Composable
fun WhislySplashScreen(
    onSplashFinished: () -> Unit
) {
    val alphaAnim = remember { Animatable(0f) }
    val scaleAnim = remember { Animatable(0.94f) }

    LaunchedEffect(Unit) {
        alphaAnim.animateTo(1f, animationSpec = tween(220, easing = FastOutSlowInEasing))
        scaleAnim.animateTo(1f, animationSpec = tween(220, easing = FastOutSlowInEasing))
        delay(320)
        onSplashFinished()
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(WhislyTokens.WarmBackground)
            .testTag("splash_screen")
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .alpha(alphaAnim.value)
                .scale(scaleAnim.value)
                .padding(32.dp)
        ) {
            com.example.components.WhislyLogo(
                size = WhislyTokens.LogoSizeHero,
                cornerRadius = 24.dp
            )

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "WHISLY",
                style = MaterialTheme.typography.displayLarge.copy(
                    letterSpacing = 2.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = WhislyTokens.PrimaryCocoa
                )
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Your voice. Every language. Anywhere.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = WhislyTokens.SecondaryBrown
                ),
                textAlign = TextAlign.Center
            )
        }
    }
}

private data class OnboardingPageData(
    val title: String,
    val subtitle: String,
    val icon: ImageVector
)

@Composable
fun WhislyOnboardingScreen(
    onFinishOnboarding: (String, String) -> Unit
) {
    val colors = WhislyDesign.colors
    val pages = remember {
        listOf(
            OnboardingPageData(
                title = "Speak in your language",
                subtitle = "Hold a natural two-way voice conversation across Tamil, Bengali, Hindi, and other Indian languages.",
                icon = Icons.Outlined.GraphicEq
            ),
            OnboardingPageData(
                title = "Whisly translates your words",
                subtitle = "Review or edit your recognized speech before translating so every sentence is clear and accurate.",
                icon = Icons.Outlined.Translate
            ),
            OnboardingPageData(
                title = "Hear the translation offline",
                subtitle = "Once your language packs are installed, Whisly translates and speaks offline with complete privacy.",
                icon = Icons.Outlined.OfflineBolt
            )
        )
    }

    var currentPage by remember { mutableIntStateOf(0) }
    var selectedSource by remember { mutableStateOf("ta") }
    var selectedTarget by remember { mutableStateOf("bn") }

    val quickLanguagePairs = listOf(
        Triple("ta", "bn", "தமிழ் (Tamil) → বাংলা (Bengali)"),
        Triple("bn", "ta", "বাংলা (Bengali) → தமிழ் (Tamil)"),
        Triple("ta", "hi", "தமிழ் (Tamil) → हिन्दी (Hindi)"),
        Triple("te", "ta", "తెలుగు (Telugu) → தமிழ் (Tamil)")
    )

    Column(
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(24.dp)
            .testTag("onboarding_screen")
    ) {
        // Top Header with Logo and Skip button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            WhislyLogoMark(size = 34.dp, showWordmark = true)

            TextButton(
                onClick = { onFinishOnboarding(selectedSource, selectedTarget) },
                modifier = Modifier.testTag("onboarding_skip_button")
            ) {
                Text(
                    text = "Skip",
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = colors.mutedText
                    )
                )
            }
        }

        // Main Animated Step Content
        AnimatedContent(
            targetState = currentPage,
            label = "onboardingPage"
        ) { pageIndex ->
            val page = pages[pageIndex]
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = WhislyTokens.PrimaryOrange.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, WhislyTokens.PrimaryOrange.copy(alpha = 0.3f)),
                    modifier = Modifier.size(88.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = page.icon,
                            contentDescription = null,
                            tint = WhislyTokens.PrimaryOrange,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = page.title,
                    style = MaterialTheme.typography.headlineLarge.copy(
                        color = colors.primaryCocoa,
                        fontWeight = FontWeight.Bold
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = page.subtitle,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = colors.mutedText
                    ),
                    textAlign = TextAlign.Center
                )

                if (pageIndex == 2) {
                    Spacer(modifier = Modifier.height(22.dp))
                    Text(
                        text = "Select default language pair:",
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = colors.primaryCocoa
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        quickLanguagePairs.forEach { (src, tgt, label) ->
                            val isSelected = selectedSource == src && selectedTarget == tgt
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) WhislyTokens.PrimaryOrange.copy(alpha = 0.12f) else colors.cardSurface,
                                border = BorderStroke(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) WhislyTokens.PrimaryOrange else colors.border
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedSource = src
                                        selectedTarget = tgt
                                    }
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = colors.primaryCocoa,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bottom Pager Dots + Continue / Get Started CTA
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                pages.indices.forEach { idx ->
                    val active = idx == currentPage
                    Box(
                        modifier = Modifier
                            .height(8.dp)
                            .width(if (active) 24.dp else 8.dp)
                            .clip(CircleShape)
                            .background(if (active) WhislyTokens.PrimaryOrange else colors.border)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            WhislyPrimaryButton(
                text = if (currentPage < pages.lastIndex) "Continue" else "Get Started",
                onClick = {
                    if (currentPage < pages.lastIndex) {
                        currentPage++
                    } else {
                        onFinishOnboarding(selectedSource, selectedTarget)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                testTag = "onboarding_primary_button"
            )
        }
    }
}
