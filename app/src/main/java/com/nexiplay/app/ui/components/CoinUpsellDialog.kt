package com.nexiplay.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nexiplay.app.ui.theme.*

@Composable
fun CoinUpsellDialog(
    title: String,
    message: String,
    buttonText: String,
    action: String,
    onDismiss: () -> Unit,
    onConfirmAction: (String) -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "upsell_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF16151D))
                .border(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(
                        listOf(
                            Color(0xFFFFD700).copy(alpha = glowAlpha),
                            Color(0xFFA855F7).copy(alpha = glowAlpha),
                            Color(0xFF00F2FE).copy(alpha = glowAlpha)
                        )
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Top close icon
                Box(modifier = Modifier.fillMaxWidth()) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.TopEnd).size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }

                // Crown Badge Icon Header
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFFFFD700), Color(0xFFFFA500))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.WorkspacePremium,
                        contentDescription = "VIP Premium",
                        tint = Color.Black,
                        modifier = Modifier.size(44.dp)
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Title
                Text(
                    text = title.ifEmpty { "Tired of Ads?" },
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = InterFont,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(8.dp))

                // Message
                Text(
                    text = message.ifEmpty {
                        "Upgrade to VIP for just 250 Coins to get 30 days of 100% Ad-Free streaming, Gold Profile Badges & Ultra HD Servers!"
                    },
                    fontSize = 13.sp,
                    fontFamily = InterFont,
                    color = Color.White.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Spacer(Modifier.height(24.dp))

                // Primary Upgrade Button
                Button(
                    onClick = {
                        onConfirmAction(action)
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent
                    ),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFFFFD700), Color(0xFFFFA500))
                                ),
                                RoundedCornerShape(14.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = buttonText.ifEmpty { "GET VIP NOW (250 🪙)" },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = InterFont,
                            color = Color.Black
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Dismiss Button
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Maybe Later",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.5f),
                        fontFamily = InterFont
                    )
                }
            }
        }
    }
}
