package com.nexiplay.app.ui.screens.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
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
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(navController: NavController) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) }
    var showForgotPasswordDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Track focus state for animated borders
    var emailFocused by remember { mutableStateOf(false) }
    var passwordFocused by remember { mutableStateOf(false) }

    // Animated border colors
    val emailBorderColor by animateColorAsState(
        targetValue = if (emailFocused) NexiRed else themeBorder(),
        animationSpec = tween(300),
        label = "emailBorder"
    )
    val passwordBorderColor by animateColorAsState(
        targetValue = if (passwordFocused) NexiRed else themeBorder(),
        animationSpec = tween(300),
        label = "passwordBorder"
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

        // Back button
        Row(Modifier.fillMaxWidth()) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Default.ArrowBack, "Back", tint = themeTextPrimary())
            }
        }

        Spacer(Modifier.height(20.dp))

        // Title section
        Text(
            "Welcome Back",
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            fontFamily = InterFont,
            color = themeTextPrimary(),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Login to your NexiPlay account",
            fontSize = 14.sp,
            fontFamily = InterFont,
            color = themeTextSecondary(),
        )

        Spacer(Modifier.height(32.dp))

        // Error message
        AnimatedVisibility(
            visible = error != null,
            enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { -it / 2 },
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

        // Success message
        AnimatedVisibility(
            visible = success != null,
            enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { -it / 2 },
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

        // Email field label
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Email",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = InterFont,
                color = themeTextSecondary(),
            )
        }
        Spacer(Modifier.height(8.dp))

        // Email field with animated focus border
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            placeholder = {
                Text(
                    "Enter your email",
                    color = themeTextTertiary(),
                    fontFamily = InterFont,
                    fontSize = 14.sp,
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Default.Email,
                    contentDescription = "Email",
                    tint = if (emailFocused) NexiRed else themeTextTertiary(),
                    modifier = Modifier.size(20.dp),
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                focusedLabelColor = NexiRed,
                cursorColor = NexiRed,
                focusedTextColor = themeTextPrimary(),
                unfocusedTextColor = themeTextPrimary(),
                focusedContainerColor = themeCard(),
                unfocusedContainerColor = themeCard(),
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = if (emailFocused) 1.5.dp else 1.dp,
                    color = emailBorderColor,
                    shape = RoundedCornerShape(14.dp),
                )
                .onFocusChanged { emailFocused = it.isFocused },
        )

        Spacer(Modifier.height(16.dp))

        // Password field label
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Password",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = InterFont,
                color = themeTextSecondary(),
            )
        }
        Spacer(Modifier.height(8.dp))

        // Password field with animated focus border
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            placeholder = {
                Text(
                    "Enter your password",
                    color = themeTextTertiary(),
                    fontFamily = InterFont,
                    fontSize = 14.sp,
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = "Password",
                    tint = if (passwordFocused) NexiRed else themeTextTertiary(),
                    modifier = Modifier.size(20.dp),
                )
            },
            singleLine = true,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        "Toggle password",
                        tint = themeTextSecondary(),
                    )
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                focusedLabelColor = NexiRed,
                cursorColor = NexiRed,
                focusedTextColor = themeTextPrimary(),
                unfocusedTextColor = themeTextPrimary(),
                focusedContainerColor = themeCard(),
                unfocusedContainerColor = themeCard(),
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = if (passwordFocused) 1.5.dp else 1.dp,
                    color = passwordBorderColor,
                    shape = RoundedCornerShape(14.dp),
                )
                .onFocusChanged { passwordFocused = it.isFocused },
        )

        Spacer(Modifier.height(8.dp))

        // Forgot Password Link
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                "Forgot Password?",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = InterFont,
                color = NexiRed,
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        showForgotPasswordDialog = true
                    }
                    .padding(vertical = 4.dp, horizontal = 8.dp)
            )
        }

        Spacer(Modifier.height(16.dp))

        // Login Button — gradient background with shimmer when loading
        val loginEnabled = !loading && email.isNotBlank() && password.isNotBlank()

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(RoundedCornerShape(14.dp))
                .then(
                    if (loading) {
                        Modifier.shimmerEffect()
                    } else {
                        Modifier.background(
                            brush = if (loginEnabled) {
                                Brush.horizontalGradient(
                                    colors = listOf(NexiGradientStart, NexiGradientEnd)
                                )
                            } else {
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        NexiGradientStart.copy(alpha = 0.4f),
                                        NexiGradientEnd.copy(alpha = 0.4f),
                                    )
                                )
                            }
                        )
                    }
                )
                .then(
                    if (loginEnabled) {
                        Modifier.bounceClick {
                            scope.launch {
                                loading = true
                                error = null
                                try {
                                    SupabaseClient.main.auth.signInWith(Email) {
                                        this.email = email.trim()
                                        this.password = password
                                    }
                                    navController.navigate(Screen.Home.route) {
                                        popUpTo(0) { inclusive = true }
                                    }
                                } catch (e: Exception) {
                                    error = e.message ?: "Login failed"
                                } finally {
                                    loading = false
                                }
                            }
                        }
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (loading) {
                // Shimmer is applied via Modifier above; show subtle loading text
                Text(
                    "Signing in…",
                    color = themeTextPrimary().copy(alpha = 0.7f),
                    fontWeight = FontWeight.Bold,
                    fontFamily = InterFont,
                    fontSize = 16.sp,
                )
            } else {
                Text(
                    "Login",
                    fontWeight = FontWeight.Bold,
                    fontFamily = InterFont,
                    fontSize = 16.sp,
                    color = themeTextPrimary(),
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // Register link
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                "Don't have an account? ",
                color = themeTextSecondary(),
                fontSize = 14.sp,
                fontFamily = InterFont,
            )
            Text(
                "Register",
                color = NexiRed,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                fontFamily = InterFont,
                modifier = Modifier.clickable { navController.navigate(Screen.Register.route) },
            )
        }
        
        // "?"? Forgot Password Dialog "?"?
        if (showForgotPasswordDialog) {
            var resetEmail by remember { mutableStateOf(email) }
            var isResetting by remember { mutableStateOf(false) }
            val context = androidx.compose.ui.platform.LocalContext.current

            AlertDialog(
                onDismissRequest = { if (!isResetting) showForgotPasswordDialog = false },
                containerColor = themeSurface(),
                titleContentColor = themeTextPrimary(),
                textContentColor = themeTextSecondary(),
                title = {
                    Text("Reset Password", fontWeight = FontWeight.Bold, fontFamily = InterFont)
                },
                text = {
                    Column {
                        Text("Enter your email address to receive a password reset link.", fontSize = 14.sp)
                        Spacer(Modifier.height(16.dp))
                        OutlinedTextField(
                            value = resetEmail,
                            onValueChange = { resetEmail = it },
                            placeholder = { Text("Email address") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = themeTextPrimary(),
                                unfocusedTextColor = themeTextPrimary(),
                            )
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (resetEmail.isBlank()) return@TextButton
                            scope.launch {
                                isResetting = true
                                try {
                                    SupabaseClient.main.auth.resetPasswordForEmail(
                                        email = resetEmail.trim(),
                                        redirectUrl = "nexiplay://reset-password"
                                    )
                                    android.widget.Toast.makeText(context, "Password reset email sent!", android.widget.Toast.LENGTH_LONG).show()
                                    showForgotPasswordDialog = false
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, e.message ?: "Failed to send email", android.widget.Toast.LENGTH_LONG).show()
                                } finally {
                                    isResetting = false
                                }
                            }
                        },
                        enabled = !isResetting
                    ) {
                        Text(if (isResetting) "Sending..." else "Send Link", color = NexiRed, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showForgotPasswordDialog = false },
                        enabled = !isResetting
                    ) {
                        Text("Cancel", color = themeTextSecondary())
                    }
                }
            )
        }
    }
}
