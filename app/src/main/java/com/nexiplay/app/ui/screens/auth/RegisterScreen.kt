package com.nexiplay.app.ui.screens.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.nexiplay.app.data.SupabaseClient
import com.nexiplay.app.ui.components.bounceClick
import com.nexiplay.app.ui.components.shimmerEffect
import com.nexiplay.app.ui.navigation.Screen
import com.nexiplay.app.ui.theme.*
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch

@Composable
fun RegisterScreen(navController: NavController) {
    var displayName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var whatsappNumber by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // ── Theme-aware input colors with animated focus ──
    val inputColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = NexiRed,
        unfocusedBorderColor = themeBorder(),
        focusedLabelColor = NexiRed,
        unfocusedLabelColor = themeTextSecondary(),
        cursorColor = NexiRed,
        focusedTextColor = themeTextPrimary(),
        unfocusedTextColor = themeTextPrimary(),
        focusedContainerColor = themeSurface(),
        unfocusedContainerColor = themeSurface(),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(themeBg())
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(40.dp))

        // ── Back button ──
        Row(Modifier.fillMaxWidth()) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Default.ArrowBack, "Back", tint = themeTextPrimary())
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Title ──
        Text(
            "Create Account",
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            fontFamily = InterFont,
            color = themeTextPrimary(),
        )
        Text(
            "Join NexiPlay today",
            fontSize = 14.sp,
            fontFamily = InterFont,
            color = themeTextSecondary(),
        )

        Spacer(Modifier.height(32.dp))

        // ── Error banner ──
        AnimatedVisibility(
            visible = error != null,
            enter = fadeIn(tween(300)) + slideInVertically { -it / 2 },
            exit = fadeOut(tween(200)),
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(NexiRed.copy(alpha = 0.1f))
                        .border(1.dp, NexiRed.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Text(
                        error ?: "",
                        fontSize = 13.sp,
                        fontFamily = InterFont,
                        color = NexiRedLight,
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
        }

        // ── Success banner ──
        AnimatedVisibility(
            visible = success != null,
            enter = fadeIn(tween(300)) + slideInVertically { -it / 2 },
            exit = fadeOut(tween(200)),
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SuccessGreen.copy(alpha = 0.1f))
                        .border(1.dp, SuccessGreen.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Text(
                        success ?: "",
                        fontSize = 13.sp,
                        fontFamily = InterFont,
                        color = SuccessGreen,
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
        }

        // ── Display Name field ──
        Text(
            "Display Name",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = InterFont,
            color = themeTextSecondary(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
        )
        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it },
            label = null,
            placeholder = { Text("Enter your display name", color = themeTextTertiary()) },
            singleLine = true,
            colors = inputColors,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))

        // ── Email field ──
        Text(
            "Email",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = InterFont,
            color = themeTextSecondary(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
        )
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = null,
            placeholder = { Text("name@example.com", color = themeTextTertiary()) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            colors = inputColors,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))

        // ── WhatsApp field ──
        Text(
            "WhatsApp Number",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = InterFont,
            color = themeTextSecondary(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
        )
        OutlinedTextField(
            value = whatsappNumber,
            onValueChange = { whatsappNumber = it },
            label = null,
            placeholder = { Text("e.g. +8801XXXXXXXXX", color = themeTextTertiary()) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            colors = inputColors,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))

        // ── Password field ──
        Text(
            "Password",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = InterFont,
            color = themeTextSecondary(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = null,
            placeholder = { Text("Min 6 characters", color = themeTextTertiary()) },
            singleLine = true,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = "Toggle password",
                        tint = themeTextSecondary(),
                    )
                }
            },
            colors = inputColors,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(28.dp))

        // ── Register button — gradient + bounceClick + shimmer loading ──
        val isEnabled = !loading && displayName.isNotBlank() && email.isNotBlank() && password.length >= 6
        val gradientBrush = Brush.horizontalGradient(
            colors = listOf(NexiGradientStart, NexiGradientEnd)
        )
        val disabledBrush = Brush.horizontalGradient(
            colors = listOf(
                NexiGradientStart.copy(alpha = 0.4f),
                NexiGradientEnd.copy(alpha = 0.4f),
            )
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (isEnabled) gradientBrush else disabledBrush)
                .then(
                    if (isEnabled) {
                        Modifier.bounceClick {
                            scope.launch {
                                loading = true; error = null
                                try {
                                    SupabaseClient.main.auth.signUpWith(Email) {
                                        this.email = email.trim()
                                        this.password = password
                                        data = kotlinx.serialization.json.buildJsonObject {
                                            put("display_name", kotlinx.serialization.json.JsonPrimitive(displayName.trim()))
                                            put("whatsapp_number", kotlinx.serialization.json.JsonPrimitive(whatsappNumber.trim()))
                                        }
                                    }
                                    success = "Account created! Please check your email (and SPAM folder) to verify."
                                } catch (e: Exception) {
                                    error = e.message ?: "Registration failed"
                                } finally { loading = false }
                            }
                        }
                    } else Modifier
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (loading) {
                // Shimmer loading indicator on the button
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .shimmerEffect()
                )
            } else {
                Text(
                    "Create Account",
                    fontWeight = FontWeight.Bold,
                    fontFamily = InterFont,
                    fontSize = 16.sp,
                    color = Color.White,
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Login link ──
        Row {
            Text(
                "Already have an account? ",
                color = themeTextSecondary(),
                fontSize = 14.sp,
                fontFamily = InterFont,
            )
            Text(
                "Login",
                color = NexiRed,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                fontFamily = InterFont,
                modifier = Modifier.clickable { navController.navigate(Screen.Login.route) },
            )
        }

        Spacer(Modifier.height(32.dp))
    }
}
