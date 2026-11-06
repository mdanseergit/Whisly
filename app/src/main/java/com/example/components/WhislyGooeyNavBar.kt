package com.example.components

import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.theme.WhislyDesign
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

data class GooeyNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String,
    val legacyAliasTag: String? = null
)

private data class GooeyParticleSpec(
    val verticalBias: Float,
    val radialSpread: Float,
    val sizeRatio: Float,
    val phaseLead: Float
)

// Fixed 6-particle spec (within requested 5-7 particle budget for low-end Android phones)
private val GOOEY_PARTICLES = listOf(
    GooeyParticleSpec(verticalBias = -0.58f, radialSpread = 0.32f, sizeRatio = 0.22f, phaseLead = 0.00f),
    GooeyParticleSpec(verticalBias = 0.54f, radialSpread = 0.28f, sizeRatio = 0.19f, phaseLead = 0.08f),
    GooeyParticleSpec(verticalBias = -0.26f, radialSpread = 0.48f, sizeRatio = 0.25f, phaseLead = 0.14f),
    GooeyParticleSpec(verticalBias = 0.30f, radialSpread = 0.44f, sizeRatio = 0.21f, phaseLead = 0.18f),
    GooeyParticleSpec(verticalBias = 0.00f, radialSpread = 0.62f, sizeRatio = 0.27f, phaseLead = 0.05f),
    GooeyParticleSpec(verticalBias = -0.42f, radialSpread = 0.18f, sizeRatio = 0.16f, phaseLead = 0.22f)
)

/**
 * React Bits GooeyNav adapted natively for Android Jetpack Compose.
 *
 * Key engineering & low-end device optimizations:
 * 1. Particle count strictly capped at 6 organic droplets (5-7 range).
 * 2. Animation duration fixed at 380ms (within 350-450ms spec) with FastOutSlowInEasing (no excessive bounce).
 * 3. Zero animation on initial page load; animation runs ONLY when the user switches tabs and
 *    stops completely once `morphProgress` reaches 1f (0% idle CPU/GPU consumption).
 * 4. Respects system reduced-motion setting (`ANIMATOR_DURATION_SCALE == 0f`) and `lowEndMode`.
 * 5. Clean translucent warm glass surface without expensive multi-pass blur lag.
 */
@Composable
fun WhislyGooeyNavBar(
    items: List<GooeyNavItem>,
    currentRoute: String,
    onNavigate: (String) -> Unit,
    lowEndMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val colors = WhislyDesign.colors

    val reducedMotion = remember {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        }.getOrDefault(false)
    }

    val targetIndex = remember(currentRoute, items) {
        items.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
    }

    var fromIndex by remember { mutableIntStateOf(targetIndex) }
    var toIndex by remember { mutableIntStateOf(targetIndex) }

    // Starts at 1f so there is NO unnecessary animation on initial page load
    val morphProgress = remember { Animatable(1f) }
    val metaballBridgePath = remember { Path() }

    LaunchedEffect(targetIndex) {
        if (targetIndex != toIndex) {
            fromIndex = toIndex
            toIndex = targetIndex
            if (reducedMotion) {
                morphProgress.snapTo(1f)
            } else {
                morphProgress.snapTo(0f)
                morphProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = 380,
                        easing = FastOutSlowInEasing
                    )
                )
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag("whisly_gooey_nav_bar")
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = colors.glassNavSurface,
            border = BorderStroke(1.dp, colors.glassNavBorder),
            shadowElevation = 4.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Layer 1: Gooey Liquid Pill + 6 Merging Droplets Canvas
                val pillColor = colors.primaryPeach
                val dropletAccentColor = colors.secondaryPeach

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 6.dp, vertical = 6.dp)
                        .testTag("gooey_nav_liquid_canvas")
                ) {
                    val itemCount = items.size.coerceAtLeast(1)
                    val slotWidth = size.width / itemCount
                    val pillHeight = size.height
                    val cornerRad = pillHeight / 2f

                    val t = morphProgress.value.coerceIn(0f, 1f)
                    val startCenterX = (fromIndex + 0.5f) * slotWidth
                    val endCenterX = (toIndex + 0.5f) * slotWidth
                    val centerY = size.height / 2f

                    if (t >= 0.995f || fromIndex == toIndex) {
                        // Settled state: single crisp pill, zero particle overhead
                        val pillWidth = slotWidth * 0.92f
                        drawRoundRect(
                            color = pillColor,
                            topLeft = Offset(endCenterX - pillWidth / 2f, 0f),
                            size = Size(pillWidth, pillHeight),
                            cornerRadius = CornerRadius(cornerRad, cornerRad)
                        )
                    } else {
                        // Liquid viscous stretch: leading edge moves faster than trailing edge
                        val leadT = (t * 1.32f).coerceIn(0f, 1f)
                        val trailT = ((t - 0.22f) / 0.78f).coerceIn(0f, 1f)

                        val leadX = startCenterX + (endCenterX - startCenterX) * leadT
                        val trailX = startCenterX + (endCenterX - startCenterX) * trailT

                        val halfPill = (slotWidth * 0.44f)
                        val leftEdge = min(leadX, trailX) - halfPill
                        val rightEdge = max(leadX, trailX) + halfPill
                        val currentWidth = (rightEdge - leftEdge).coerceAtLeast(slotWidth * 0.88f)

                        // Necking pinch in the middle during peak velocity (t around 0.45)
                        val stretchEnvelope = sin(t * PI.toFloat())
                        val dynamicHeight = pillHeight * (1f - 0.12f * stretchEnvelope)
                        val topY = (pillHeight - dynamicHeight) / 2f

                        // Draw main liquid stretched pill
                        drawRoundRect(
                            color = pillColor,
                            topLeft = Offset(leftEdge, topY),
                            size = Size(currentWidth, dynamicHeight),
                            cornerRadius = CornerRadius(dynamicHeight / 2f, dynamicHeight / 2f)
                        )

                        // Draw organic metaball neck & 6 merging droplets (unless lowEndMode is active)
                        if (!lowEndMode) {
                            metaballBridgePath.reset()
                            val midX = (leadX + trailX) / 2f
                            metaballBridgePath.moveTo(trailX, topY + dynamicHeight * 0.18f)
                            metaballBridgePath.quadraticTo(
                                midX,
                                centerY,
                                leadX,
                                topY + dynamicHeight * 0.18f
                            )
                            metaballBridgePath.lineTo(leadX, topY + dynamicHeight * 0.82f)
                            metaballBridgePath.quadraticTo(
                                midX,
                                centerY,
                                trailX,
                                topY + dynamicHeight * 0.82f
                            )
                            metaballBridgePath.close()
                            drawPath(
                                path = metaballBridgePath,
                                color = dropletAccentColor.copy(alpha = 0.65f * stretchEnvelope)
                            )

                            // 6 organic Gooey droplets detaching and merging into target pill
                            for (pSpec in GOOEY_PARTICLES) {
                                val localT = ((t - pSpec.phaseLead) / (1f - pSpec.phaseLead)).coerceIn(0f, 1f)
                                val burstEnv = sin(localT * PI.toFloat())
                                if (burstEnv > 0.02f) {
                                    val px = startCenterX + (endCenterX - startCenterX) * localT +
                                        (endCenterX - startCenterX) * 0.12f * pSpec.radialSpread * burstEnv
                                    val py = centerY + (pillHeight * 0.46f) * pSpec.verticalBias * burstEnv
                                    val radius = (pillHeight * pSpec.sizeRatio) * burstEnv
                                    drawCircle(
                                        color = if (pSpec.verticalBias > 0) pillColor else dropletAccentColor,
                                        radius = radius,
                                        center = Offset(px, py),
                                        alpha = (0.88f * burstEnv).coerceIn(0f, 1f)
                                    )
                                }
                            }
                        }
                    }
                }

                // Layer 2: Accessible Touch Targets, Icons, and Compact Labels
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 6.dp)
                ) {
                    items.forEach { item ->
                        val isSelected = item.route == currentRoute
                        val contentColor by animateColorAsState(
                            targetValue = if (isSelected) {
                                colors.deepBrown
                            } else {
                                colors.mutedText
                            },
                            animationSpec = tween(220),
                            label = "navItemColor_${item.route}"
                        )

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(22.dp))
                                .semantics {
                                    role = Role.Tab
                                    selected = isSelected
                                }
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {
                                        if (!isSelected) {
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onNavigate(item.route)
                                        }
                                    }
                                )
                                .testTag(item.testTag)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.size(22.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label,
                                        tint = contentColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = item.label,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = contentColor,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 11.sp,
                                        lineHeight = 12.sp,
                                        platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                                            includeFontPadding = false
                                        )
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
