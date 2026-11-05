package com.nexiplay.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.nexiplay.app.ui.theme.ThemeState

/**
 * Premium Shimmer Effect — theme-aware (light or dark).
 * Drawn strictly in the Draw Phase (`drawBehind`) for zero recomposition and zero layout thrashing.
 */
fun Modifier.shimmerEffect(): Modifier = composed {
    val isDark = ThemeState.isDarkMode
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = -1.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_x"
    )

    val colors = remember(isDark) {
        if (isDark) {
            listOf(Color(0xFF1E1E1E), Color(0xFF333333), Color(0xFF1E1E1E))
        } else {
            listOf(Color(0xFFE8E8E8), Color(0xFFF5F5F5), Color(0xFFE8E8E8))
        }
    }

    this.drawBehind {
        val w = size.width.coerceAtLeast(1f)
        val h = size.height.coerceAtLeast(1f)
        val startX = progress * w
        drawRect(
            brush = Brush.linearGradient(
                colors = colors,
                start = Offset(startX, 0f),
                end = Offset(startX + w, h)
            )
        )
    }
}

/**
 * Shimmer placeholder box.
 */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    cornerRadius: Int = 8,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius.dp))
            .shimmerEffect()
    )
}

/**
 * Zero-Scroll-Lag Tactile Press Effect:
 * Uses `collectIsPressedAsState()` which respects scroll container tap delay (`TapIndicationDelay`)
 * so scrolling LazyColumn / LazyRow / LazyVerticalGrid NEVER triggers press animations or recompositions.
 * Only attaches `graphicsLayer` when actively pressed/animating (`scale != 1f`).
 */
fun Modifier.bounceClick(
    scaleDown: Float = 0.96f,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) scaleDown else 1f,
        animationSpec = tween(durationMillis = 100, easing = FastOutSlowInEasing),
        label = "bounce"
    )

    this
        .then(
            if (scale != 1f) {
                Modifier.graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
            } else {
                Modifier
            }
        )
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
}
