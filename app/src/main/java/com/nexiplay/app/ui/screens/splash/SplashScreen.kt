package com.nexiplay.app.ui.screens.splash

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.nexiplay.app.R
import com.nexiplay.app.ui.navigation.Screen
import com.nexiplay.app.ui.theme.InterFont
import com.nexiplay.app.ui.theme.NexiRed
import com.nexiplay.app.ui.theme.NexiRedLight
import com.nexiplay.app.ui.theme.SuccessGreen
import com.nexiplay.app.ui.theme.TextGray
import com.nexiplay.app.ui.theme.TextWhite
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(navController: NavController) {
    var startAnimation by remember { mutableStateOf(false) }

    val emblemScale by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.68f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "emblemScale"
    )

    val contentAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing),
        label = "contentAlpha"
    )

    val progressFraction by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.08f,
        animationSpec = tween(durationMillis = 1250, easing = FastOutSlowInEasing),
        label = "progressFraction"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "splashInfinite")
    val orbitAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbitAngle"
    )

    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseGlow"
    )

    LaunchedEffect(Unit) {
        startAnimation = true
        val hasPendingDeepLink = !com.nexiplay.app.ui.navigation.NotificationRouter.pendingRoute.value.isNullOrBlank()
        delay(if (hasPendingDeepLink) 420L else 1380L)
        navController.navigate(Screen.Home.route) {
            popUpTo(Screen.Splash.route) { inclusive = true }
            launchSingleTop = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0E0A1A),
                        Color(0xFF090710),
                        Color(0xFF06050B)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Ambient background radial studio spotlight
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width * 0.5f, size.height * 0.44f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF9273F5).copy(alpha = 0.24f * pulseGlow),
                        Color(0xFF6D28D9).copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = size.minDimension * 0.62f
                ),
                radius = size.minDimension * 0.62f,
                center = center
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .graphicsLayer {
                    alpha = contentAlpha
                }
        ) {
            // ── Layered 3D Emblem with Live Rotating Orbital Energy Ring ──
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(188.dp)
                    .graphicsLayer {
                        scaleX = emblemScale
                        scaleY = emblemScale
                    }
            ) {
                // Rotating dual-arc orbital ring
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val ringStroke = 2.5.dp.toPx()
                    // Subtle outer glass ring
                    drawCircle(
                        color = Color.White.copy(alpha = 0.06f),
                        radius = size.minDimension * 0.46f,
                        style = Stroke(width = 1.dp.toPx())
                    )

                    rotate(degrees = orbitAngle) {
                        drawArc(
                            brush = Brush.sweepGradient(
                                listOf(
                                    Color.Transparent,
                                    Color(0xFF9273F5).copy(alpha = 0.85f),
                                    Color(0xFFB09AFF),
                                    Color(0xFF5C46B2),
                                    Color.Transparent
                                )
                            ),
                            startAngle = 0f,
                            sweepAngle = 220f,
                            useCenter = false,
                            style = Stroke(width = ringStroke, cap = StrokeCap.Round)
                        )
                    }
                }

                // Clean Transparent 3D 'N' Play Emblem (no square box, no baked-in text)
                Image(
                    painter = painterResource(id = R.drawable.ic_emblem_clean),
                    contentDescription = "NexiPlay Emblem",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(144.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // ── Crisp Vector Typography Wordmark (`NEXIPLAY`) ──
            Text(
                text = buildAnnotatedString {
                    withStyle(
                        SpanStyle(
                            color = TextWhite,
                            fontWeight = FontWeight.Black
                        )
                    ) {
                        append("NEXI")
                    }
                    withStyle(
                        SpanStyle(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFFC4B5FD),
                                    Color(0xFF9273F5),
                                    Color(0xFF7C3AED)
                                )
                            ),
                            fontWeight = FontWeight.Black
                        )
                    ) {
                        append("PLAY")
                    }
                },
                fontFamily = InterFont,
                fontSize = 32.sp,
                letterSpacing = 1.2.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ── Category Tagline Pill ──
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.04f))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "MOVIES",
                    color = TextGray,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = InterFont,
                    letterSpacing = 1.5.sp
                )
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .background(NexiRedLight, CircleShape)
                )
                Text(
                    text = "ANIME",
                    color = TextWhite,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = InterFont,
                    letterSpacing = 1.5.sp
                )
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .background(SuccessGreen, CircleShape)
                )
                Text(
                    text = "WEB SERIES",
                    color = TextGray,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = InterFont,
                    letterSpacing = 1.5.sp
                )
            }

            Spacer(modifier = Modifier.height(34.dp))

            // ── Sleek Industrial Progress Bar ──
            Box(
                modifier = Modifier
                    .width(156.dp)
                    .height(3.5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.08f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progressFraction)
                        .clip(RoundedCornerShape(50))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    NexiRed,
                                    NexiRedLight,
                                    SuccessGreen
                                )
                            )
                        )
                )
            }
        }
    }
}
