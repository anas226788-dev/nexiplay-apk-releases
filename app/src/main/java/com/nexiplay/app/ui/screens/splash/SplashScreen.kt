package com.nexiplay.app.ui.screens.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.nexiplay.app.ui.navigation.Screen
import com.nexiplay.app.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(navController: NavController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var startAnimation by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.5f,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "scale"
    )

    val alpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 800, easing = LinearEasing),
        label = "alpha"
    )

    val loadingWidth by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
        label = "loading"
    )

    // Cinematic slow scale animation for the whole content
    val cinematicScale by animateFloatAsState(
        targetValue = if (startAnimation) 1.1f else 0.8f,
        animationSpec = tween(durationMillis = 2000, easing = LinearOutSlowInEasing),
        label = "cinematicScale"
    )

    LaunchedEffect(key1 = true) {
        startAnimation = true
        delay(2000)
        com.nexiplay.app.data.util.AdManager.showAppOpenAd(context) {
            navController.navigate(Screen.Home.route) {
                popUpTo("splash") { inclusive = true }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF030303)), // Pure dark background
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .alpha(alpha)
                .scale(cinematicScale) // Slow scaling effect
        ) {
            // Minimalistic Large Logo
            Text(
                text = "N",
                fontSize = 80.sp,
                fontWeight = FontWeight.Black,
                fontFamily = InterFont,
                color = NexiRed
            )

            Spacer(modifier = Modifier.height(12.dp))

            // App Name
            Row {
                Text(
                    text = "NEXI",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = InterFont,
                    color = Color.White
                )
                Text(
                    text = "PLAY",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = InterFont,
                    color = NexiRed
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "START STREAMING",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                color = Color.Gray,
                fontFamily = InterFont
            )

            Spacer(modifier = Modifier.height(60.dp))

            // Very Minimal Loading Bar
            Box(
                modifier = Modifier
                    .width(140.dp)
                    .height(2.dp) // Ultra thin
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF111111)) // Very dark background for bar
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(loadingWidth)
                        .clip(RoundedCornerShape(50))
                        .background(NexiRed)
                )
            }
        }
    }
}
