package com.nexiplay.app.ui.screens.novels

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexiplay.app.ui.components.ShimmerBox
import com.nexiplay.app.ui.components.shimmerEffect
import com.nexiplay.app.ui.theme.*

/**
 * Modern Pulsing Book Icon Header for Novel Loading screens.
 */
@Composable
fun AnimatedNovelHeaderLoader(message: String = "Loading Novels...") {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .scale(scale)
                .background(
                    Brush.radialGradient(
                        colors = listOf(NexiRed.copy(alpha = 0.4f), Color.Transparent)
                    ),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        Brush.linearGradient(listOf(NexiRed, VipPurple)),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MenuBook,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = message,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = themeTextSecondary(),
            fontFamily = InterFont
        )
    }
}

/**
 * Modern Skeleton Grid for Novels Screen
 */
@Composable
fun NovelGridSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(themeBg())
    ) {
        AnimatedNovelHeaderLoader("Discovering Novels...")

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(6) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Cover Skeleton
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.68f),
                        cornerRadius = 14
                    )
                    Spacer(Modifier.height(8.dp))
                    // Title Skeleton
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(14.dp),
                        cornerRadius = 4
                    )
                    Spacer(Modifier.height(6.dp))
                    // Subtitle Skeleton
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(0.45f)
                            .height(10.dp),
                        cornerRadius = 4
                    )
                }
            }
        }
    }
}

/**
 * Modern Skeleton for Novel Detail Screen
 */
@Composable
fun NovelDetailSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(themeBg())
            .padding(16.dp)
    ) {
        // Hero Header Skeleton
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ShimmerBox(
                modifier = Modifier
                    .width(120.dp)
                    .height(170.dp),
                cornerRadius = 14
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(20.dp),
                    cornerRadius = 4
                )
                Spacer(Modifier.height(10.dp))
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(14.dp),
                    cornerRadius = 4
                )
                Spacer(Modifier.height(12.dp))
                ShimmerBox(
                    modifier = Modifier
                        .width(70.dp)
                        .height(22.dp),
                    cornerRadius = 6
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // Start Reading Button Skeleton
        ShimmerBox(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            cornerRadius = 12
        )

        Spacer(Modifier.height(24.dp))

        // Description Header & Paragraph
        ShimmerBox(
            modifier = Modifier
                .width(100.dp)
                .height(16.dp),
            cornerRadius = 4
        )
        Spacer(Modifier.height(8.dp))
        ShimmerBox(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp),
            cornerRadius = 4
        )
        Spacer(Modifier.height(6.dp))
        ShimmerBox(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(12.dp),
            cornerRadius = 4
        )
        Spacer(Modifier.height(6.dp))
        ShimmerBox(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .height(12.dp),
            cornerRadius = 4
        )

        Spacer(Modifier.height(24.dp))

        // Chapters List Skeleton
        ShimmerBox(
            modifier = Modifier
                .width(110.dp)
                .height(16.dp),
            cornerRadius = 4
        )
        Spacer(Modifier.height(12.dp))

        repeat(5) {
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .padding(vertical = 4.dp),
                cornerRadius = 10
            )
        }
    }
}

/**
 * Modern Skeleton for Novel Reader Screen
 */
@Composable
fun NovelReaderSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(themeBg())
            .padding(16.dp)
    ) {
        AnimatedNovelHeaderLoader("Opening Chapter...")

        Spacer(Modifier.height(16.dp))

        // Title Line
        ShimmerBox(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(22.dp),
            cornerRadius = 4
        )
        Spacer(Modifier.height(16.dp))

        // Text Paragraph Skeletons
        repeat(8) { index ->
            val fillFraction = when (index % 4) {
                0 -> 1f
                1 -> 0.92f
                2 -> 0.97f
                else -> 0.65f
            }
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth(fillFraction)
                    .height(14.dp),
                cornerRadius = 4
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}
