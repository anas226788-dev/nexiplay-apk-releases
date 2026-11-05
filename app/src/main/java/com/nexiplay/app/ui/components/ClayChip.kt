package com.nexiplay.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexiplay.app.ui.theme.ClayRedGradient
import com.nexiplay.app.ui.theme.DarkBorder
import com.nexiplay.app.ui.theme.DarkSurface
import com.nexiplay.app.ui.theme.DarkSurface2
import com.nexiplay.app.ui.theme.GlassBorderBrush
import com.nexiplay.app.ui.theme.NexiRed
import com.nexiplay.app.ui.theme.TextGray
import com.nexiplay.app.ui.theme.TextWhite

/**
 * Tactile ClayChip for filter pills, episode tabs, genre chips, and novel categories.
 * Active state glows crimson with 3D tactile elevation. Resting state sits comfortably in dark neumorphic surface.
 */
@Composable
fun ClayChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "clayChipScale"
    )

    val textColor by animateColorAsState(
        targetValue = if (isSelected) TextWhite else TextGray,
        label = "clayChipTextColor"
    )

    val shape = RoundedCornerShape(20.dp)

    Box(
        modifier = modifier
            .scale(scale)
            .then(
                if (isSelected) {
                    Modifier.shadow(
                        elevation = 4.dp,
                        shape = shape,
                        ambientColor = NexiRed.copy(alpha = 0.35f),
                        spotColor = NexiRed.copy(alpha = 0.5f)
                    )
                } else {
                    Modifier
                }
            )
            .clip(shape)
            .background(
                brush = if (isSelected) {
                    ClayRedGradient
                } else {
                    Brush.verticalGradient(listOf(DarkSurface2.copy(alpha = 0.7f), DarkSurface.copy(alpha = 0.85f)))
                },
                shape = shape
            )
            .border(
                width = 1.dp,
                brush = if (isSelected) {
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0.05f))
                    )
                } else {
                    GlassBorderBrush
                },
                shape = shape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}
