package com.example.components

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.theme.InterFontFamily
import com.example.core.theme.WhislyDesign
import com.example.core.theme.WhislyTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn

/**
 * Distinct Connectivity & Local Pack Readiness States for the Whisly Top Bar.
 *
 * Truthfully separates network connectivity from installed language packs:
 * - ONLINE: Device has internet connectivity and required language packs are installed ("Online")
 * - OFFLINE: Device has no internet connectivity ("Offline")
 * - OFFLINE_READY: Device is offline, and required language packs for the active pair are installed ("Offline")
 * - PACK_NEEDED: A required language pack for the active pair is not yet downloaded ("Pack Needed")
 */
enum class WhislyConnectivityState(val label: String) {
    ONLINE("Online"),
    OFFLINE("Offline"),
    OFFLINE_READY("Offline"),
    PACK_NEEDED("Pack Needed")
}

/**
 * Resolves the top-bar status from live network connectivity and installed language-pack state.
 */
fun resolveConnectivityStatus(
    isOnline: Boolean,
    isPairVerifiedOffline: Boolean,
    preferPureOfflineWhenDisconnected: Boolean = false
): WhislyConnectivityState {
    return when {
        !isPairVerifiedOffline -> if (!isOnline && preferPureOfflineWhenDisconnected) {
            WhislyConnectivityState.OFFLINE
        } else {
            WhislyConnectivityState.PACK_NEEDED
        }
        !isOnline -> if (preferPureOfflineWhenDisconnected) {
            WhislyConnectivityState.OFFLINE
        } else {
            WhislyConnectivityState.OFFLINE_READY
        }
        else -> WhislyConnectivityState.ONLINE
    }
}

/**
 * Observes Android network connectivity via ConnectivityManager on Dispatchers.IO.
 */
fun observeNetworkConnectivityFlow(context: Context): Flow<Boolean> = callbackFlow {
    val appContext = context.applicationContext
    val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    if (cm == null) {
        trySend(false)
        close()
        return@callbackFlow
    }

    fun queryActiveNetwork(): Boolean {
        return runCatching {
            val network = cm.activeNetwork ?: return@runCatching false
            val capabilities = cm.getNetworkCapabilities(network) ?: return@runCatching false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }.getOrDefault(false)
    }

    trySend(queryActiveNetwork())

    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            trySend(queryActiveNetwork())
        }

        override fun onLost(network: Network) {
            trySend(queryActiveNetwork())
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            trySend(hasInternet)
        }
    }

    runCatching {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, callback)
    }

    awaitClose {
        runCatching { cm.unregisterNetworkCallback(callback) }
    }
}.distinctUntilChanged().flowOn(Dispatchers.IO)

/**
 * Canonical Reusable `WhislyLogo` Composable.
 *
 * Renders the centered Whisly W-ribbon logo inside a warm-white rounded-square container
 * with `ContentScale.Fit` and equal padding on all sides.
 */
@Composable
fun WhislyLogo(
    size: Dp = WhislyTokens.LogoSizeHeader,
    containerColor: Color = WhislyTokens.WarmWhite,
    borderColor: Color = WhislyTokens.HeaderSecondaryText.copy(alpha = 0.35f),
    cornerRadius: Dp = (size.value * 0.25f).dp,
    modifier: Modifier = Modifier
) {
    val innerPadding = (size.value * 0.08f).dp
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(containerColor)
            .border(0.75.dp, borderColor, RoundedCornerShape(cornerRadius))
            .padding(innerPadding)
            .testTag("whisly_logo_box")
    ) {
        Image(
            painter = painterResource(id = R.drawable.whisly_logo),
            contentDescription = "Whisly Logo",
            contentScale = ContentScale.Fit,
            alignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * Left Section — Brand Identity (`WhislyBrand`).
 *
 * Uses `WhislyLogo` + `"WHISLY"` in Bold 700 with tuned letter-spacing, vertically centered.
 */
@Composable
fun WhislyBrand(
    size: Dp = WhislyTokens.LogoSizeHeader,
    wordmarkColor: Color = WhislyTokens.WarmWhite,
    showWordmark: Boolean = true,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.testTag("whisly_brand")
    ) {
        WhislyLogo(size = size)
        if (showWordmark) {
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "WHISLY",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    letterSpacing = 1.6.sp,
                    color = wordmarkColor,
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                ),
                modifier = Modifier.testTag("whisly_wordmark")
            )
        }
    }
}

/**
 * Compatibility wrapper for screens that render the Whisly mark on light/dark surfaces.
 */
@Composable
fun WhislyLogoMark(
    size: Dp = WhislyTokens.LogoSizeHeader,
    showWordmark: Boolean = true,
    modifier: Modifier = Modifier
) {
    val colors = WhislyDesign.colors
    WhislyBrand(
        size = size,
        wordmarkColor = colors.primaryCocoa,
        showWordmark = showWordmark,
        modifier = modifier
    )
}

/**
 * Right Section — Compact Connectivity Indicator (`ConnectivityStatus`).
 *
 * Phase 2 spec:
 * - Small dot + short label, no glow or large badge
 * - Designed for the Deep Cocoa (#29211E) top bar with Warm White (#FFF9F5) content
 */
@Composable
fun ConnectivityStatus(
    isOnline: Boolean,
    isPairVerifiedOffline: Boolean,
    onClick: () -> Unit,
    overrideState: WhislyConnectivityState? = null,
    modifier: Modifier = Modifier
) {
    val status = overrideState ?: resolveConnectivityStatus(
        isOnline = isOnline,
        isPairVerifiedOffline = isPairVerifiedOffline
    )

    var isPressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1.0f,
        animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
        label = "statusPressScale"
    )
    val pressAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.82f else 1.0f,
        animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
        label = "statusPressAlpha"
    )

    val targetDotColor = when (status) {
        WhislyConnectivityState.ONLINE -> WhislyTokens.PrimaryPeach
        WhislyConnectivityState.OFFLINE -> WhislyTokens.HeaderSecondaryText
        WhislyConnectivityState.OFFLINE_READY -> WhislyTokens.PrimaryPeach
        WhislyConnectivityState.PACK_NEEDED -> WhislyTokens.GoldenAccent
    }

    val targetTextColor = when (status) {
        WhislyConnectivityState.ONLINE -> WhislyTokens.WarmWhite
        WhislyConnectivityState.OFFLINE -> WhislyTokens.HeaderSecondaryText
        WhislyConnectivityState.OFFLINE_READY -> WhislyTokens.WarmWhite
        WhislyConnectivityState.PACK_NEEDED -> WhislyTokens.PrimaryPeach
    }

    val dotColor by animateColorAsState(
        targetValue = targetDotColor,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "statusDotColor"
    )
    val textColor by animateColorAsState(
        targetValue = targetTextColor,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "statusTextColor"
    )

    val chipShape = RoundedCornerShape(50)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .scale(pressScale)
            .alpha(pressAlpha)
            .clip(chipShape)
            .background(WhislyTokens.WarmBrown.copy(alpha = 0.55f))
            .border(1.dp, WhislyTokens.HeaderSecondaryText.copy(alpha = 0.24f), chipShape)
            .semantics {
                role = Role.Button
                contentDescription = status.label
            }
            .pointerInput(onClick) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onTap = { onClick() }
                )
            }
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .testTag("offline_status_pill")
    ) {
        AnimatedContent(
            targetState = status,
            transitionSpec = {
                fadeIn(animationSpec = tween(180, easing = FastOutSlowInEasing)) togetherWith
                    fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing))
            },
            label = "connectivityStatusTransition"
        ) { currentStatus ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = currentStatus.label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = textColor,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp
                    )
                )
            }
        }
    }
}

/**
 * Backwards-compatible alias for `ConnectivityStatus`.
 */
@Composable
fun OfflineStatusPill(
    isPairVerifiedOffline: Boolean,
    offlineOnlyMode: Boolean,
    onClick: () -> Unit,
    isOnline: Boolean = false,
    overrideState: WhislyConnectivityState? = null,
    modifier: Modifier = Modifier
) {
    ConnectivityStatus(
        isOnline = isOnline,
        isPairVerifiedOffline = isPairVerifiedOffline,
        onClick = onClick,
        overrideState = overrideState,
        modifier = modifier
    )
}

/**
 * Minimal Theme Toggle Button (`ThemeToggleButton`) with subtle icon crossfade.
 */
@Composable
fun ThemeToggleButton(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "top_theme_toggle_button"
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1.0f,
        animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
        label = "themeBtnScale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (isPressed) 0.75f else 1.0f,
        animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
        label = "themeBtnAlpha"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .scale(scale)
            .alpha(alpha)
            .clip(RoundedCornerShape(WhislyTokens.RadiusControl))
            .semantics {
                role = Role.Button
                contentDescription = if (isDarkTheme) "Switch to Light Mode" else "Switch to Dark Mode"
            }
            .pointerInput(onToggleTheme) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onTap = { onToggleTheme() }
                )
            }
            .testTag(testTag)
    ) {
        AnimatedContent(
            targetState = isDarkTheme,
            transitionSpec = {
                fadeIn(animationSpec = tween(200, easing = FastOutSlowInEasing)) togetherWith
                    fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing))
            },
            label = "themeIconTransition"
        ) { dark ->
            Icon(
                imageVector = if (dark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                contentDescription = null,
                tint = WhislyTokens.WarmWhite,
                modifier = Modifier.size(21.dp)
            )
        }
    }
}

/**
 * Phase 2 — Whisly Top Navigation Bar (`WhislyTopBar`).
 *
 * - Deep Cocoa (`#29211E`) background in both Light and Dark themes, with Warm White (`#FFF9F5`) content
 * - Left: `WhislyLogo` + `"WHISLY"` in Bold 700 with tuned letter-spacing, vertically centered
 * - Right: compact connectivity indicator (small dot + short label) and theme toggle with subtle icon crossfade
 */
@Composable
fun WhislyTopBar(
    isOnline: Boolean,
    isPairVerifiedOffline: Boolean,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onStatusClick: () -> Unit,
    overrideConnectivityState: WhislyConnectivityState? = null,
    themeToggleTestTag: String = "top_theme_toggle_button",
    modifier: Modifier = Modifier
) {
    Surface(
        color = WhislyTokens.HeaderCocoaBackground,
        shadowElevation = 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("whisly_top_bar")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = WhislyTokens.ScreenHorizontalPadding)
        ) {
            WhislyBrand(
                size = WhislyTokens.LogoSizeHeader,
                wordmarkColor = WhislyTokens.WarmWhite,
                showWordmark = true
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ConnectivityStatus(
                    isOnline = isOnline,
                    isPairVerifiedOffline = isPairVerifiedOffline,
                    onClick = onStatusClick,
                    overrideState = overrideConnectivityState
                )

                ThemeToggleButton(
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme,
                    testTag = themeToggleTestTag
                )
            }
        }
    }
}

@Composable
fun WhislyPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTag: String = "whisly_primary_button"
) {
    val colors = WhislyDesign.colors
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(WhislyTokens.RadiusButtonInput),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.primaryAccent,
            contentColor = colors.onPrimaryAccent,
            disabledContainerColor = colors.secondaryPeach.copy(alpha = 0.45f),
            disabledContentColor = colors.deepBrown.copy(alpha = 0.6f)
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        modifier = modifier
            .height(52.dp)
            .testTag(testTag)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(
                color = colors.onPrimaryAccent,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

@Composable
fun WhislySecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "whisly_secondary_button"
) {
    val colors = WhislyDesign.colors
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(WhislyTokens.RadiusButtonInput),
        border = BorderStroke(1.dp, colors.border),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = colors.cardSurface,
            contentColor = colors.primaryCocoa
        ),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        modifier = modifier
            .height(48.dp)
            .testTag(testTag)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(
                color = colors.primaryCocoa,
                fontWeight = FontWeight.SemiBold
            )
        )
    }
}
