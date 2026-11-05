package com.nexiplay.app.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nexiplay.app.ui.theme.DarkBorder
import com.nexiplay.app.ui.theme.DarkSurface
import com.nexiplay.app.ui.theme.GlassBorderBrush

/**
 * High-performance GlassSurface for NexiPlay Hybrid UI:
 * - API 31+ (Android 12+): Uses native hardware RenderEffect blur
 * - API 24-30 (Android 7-11): Uses solid translucent gradient fallback to guarantee 60fps
 *   on budget devices (never lags during scrolling or animations).
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    blurRadius: Dp = 20.dp,
    backgroundColor: Color = DarkSurface.copy(alpha = 0.82f),
    borderBrush: Brush = GlassBorderBrush,
    borderWidth: Dp = 1.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val isBlurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val interactionSource = remember { MutableInteractionSource() }

    val baseModifier = modifier
        .clip(shape)
        .then(
            if (isBlurSupported) {
                Modifier.graphicsLayer {
                    val px = blurRadius.toPx()
                    if (px > 0f) {
                        renderEffect = android.graphics.RenderEffect
                            .createBlurEffect(px, px, android.graphics.Shader.TileMode.CLAMP)
                            .asComposeRenderEffect()
                    }
                }
            } else {
                Modifier
            }
        )
        .background(
            if (isBlurSupported) backgroundColor else DarkSurface.copy(alpha = 0.94f),
            shape = shape
        )
        .border(
            width = borderWidth,
            brush = borderBrush,
            shape = shape
        )
        .then(
            if (onClick != null) {
                Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
            } else {
                Modifier
            }
        )

    Box(
        modifier = baseModifier,
        content = content
    )
}

/**
 * Solid glass panel - strictly zero-blur surface with glass-like 1px border.
 * Use for poster cards and large grid items to preserve memory & 60fps scrolling.
 */
@Composable
fun SolidGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    backgroundColor: Color = DarkSurface,
    borderColor: Color = DarkBorder,
    borderWidth: Dp = 1.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    val baseModifier = modifier
        .clip(shape)
        .background(backgroundColor, shape = shape)
        .border(width = borderWidth, color = borderColor, shape = shape)
        .then(
            if (onClick != null) {
                Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
            } else {
                Modifier
            }
        )

    Box(
        modifier = baseModifier,
        content = content
    )
}
