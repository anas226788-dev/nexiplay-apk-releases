package com.nexiplay.app.ui.screens.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.nexiplay.app.data.SupabaseClient
import com.nexiplay.app.data.model.UserProfile
import com.nexiplay.app.ui.components.ShimmerBox
import com.nexiplay.app.ui.components.bounceClick
import com.nexiplay.app.ui.navigation.Screen
import com.nexiplay.app.ui.theme.*
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.nexiplay.app.data.util.ImgBBUploader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

@Composable
fun ProfileScreen(navController: NavController) {
    var profile by remember { mutableStateOf<UserProfile?>(null) }
    var isLoggedIn by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var isLeaderboardEnabled by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        // Check leaderboard toggle in app_settings
        try {
            val json = SupabaseClient.main.from("app_settings")
                .select(Columns.list("is_leaderboard_enabled")) { filter { eq("id", 1) } }
                .decodeSingleOrNull<kotlinx.serialization.json.JsonObject>()
            val enabled = json?.get("is_leaderboard_enabled")?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
            if (enabled == false) {
                isLeaderboardEnabled = false
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val user = SupabaseClient.main.auth.currentUserOrNull()
        isLoggedIn = user != null
        if (user != null) {
            try {
                var p = SupabaseClient.main.from("profiles")
                    .select(Columns.list(
                        "id", "email", "display_name", "avatar_url",
                        "whatsapp_number", "hide_nsfw", "referral_code",
                        "vip_badge", "vip_badge_expires", "ad_free_until"
                    )) { filter { eq("id", user.id) } }
                    .decodeSingleOrNull<UserProfile>()
                
                val meta = user.userMetadata
                val metaName = meta?.get("display_name")?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.contentOrNull else null }
                val metaAvatar = meta?.get("avatar_url")?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.contentOrNull else null }
                val metaWhatsapp = meta?.get("whatsapp_number")?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.contentOrNull else null }

                profile = p?.copy(
                    displayName = metaName ?: p.displayName,
                    avatarUrl = metaAvatar ?: p.avatarUrl,
                    whatsappNumber = metaWhatsapp ?: p.whatsappNumber
                ) ?: UserProfile(
                    id = user.id,
                    email = user.email,
                    displayName = metaName,
                    avatarUrl = metaAvatar,
                    whatsappNumber = metaWhatsapp
                )
            } catch (e: Exception) { 
                e.printStackTrace()
            }
        }
        loading = false
    }

    var isUploading by remember { mutableStateOf(false) }
    var showEditNameDialog by remember { mutableStateOf(false) }
    var editNameInput by remember { mutableStateOf("") }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            isUploading = true
            scope.launch {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.readBytes()
                }
                if (bytes != null) {
                    val uploadedUrl = ImgBBUploader.uploadImage(bytes)
                    if (uploadedUrl != null) {
                        val user = SupabaseClient.main.auth.currentUserOrNull()
                        if (user != null) {
                            try {
                                // 1. Update Auth Metadata
                                SupabaseClient.main.auth.updateUser {
                                    data = kotlinx.serialization.json.buildJsonObject {
                                        put("avatar_url", kotlinx.serialization.json.JsonPrimitive(uploadedUrl))
                                    }
                                }
                                // 2. Update Profiles Table
                                SupabaseClient.main.from("profiles")
                                    .update(mapOf(
                                        "avatar_url" to uploadedUrl,
                                        "updated_at" to java.time.Instant.now().toString()
                                    )) {
                                        filter { eq("id", user.id) }
                                    }
                                profile = profile?.copy(avatarUrl = uploadedUrl) 
                                    ?: UserProfile(id = user.id, avatarUrl = uploadedUrl)
                                Toast.makeText(context, "Profile photo updated!", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                e.printStackTrace()
                                Toast.makeText(context, "DB Error: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    } else {
                        Toast.makeText(context, "Failed to upload photo", Toast.LENGTH_SHORT).show()
                    }
                }
                isUploading = false
            }
        }
    }

    if (showEditNameDialog) {
        AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            title = { Text("Edit Name", fontFamily = InterFont, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = editNameInput,
                    onValueChange = { editNameInput = it },
                    label = { Text("Display Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NexiRed,
                        focusedLabelColor = NexiRed
                    )
                )
            },
            confirmButton = {
                Button(onClick = {
                    val user = SupabaseClient.main.auth.currentUserOrNull()
                    if (user != null && editNameInput.isNotBlank()) {
                        scope.launch {
                            try {
                                // 1. Update Auth Metadata
                                SupabaseClient.main.auth.updateUser {
                                    data = kotlinx.serialization.json.buildJsonObject {
                                        put("display_name", kotlinx.serialization.json.JsonPrimitive(editNameInput))
                                    }
                                }
                                // 2. Update Profiles Table
                                SupabaseClient.main.from("profiles")
                                    .update(mapOf(
                                        "display_name" to editNameInput,
                                        "updated_at" to java.time.Instant.now().toString()
                                    )) {
                                        filter { eq("id", user.id) }
                                    }
                                profile = profile?.copy(displayName = editNameInput) 
                                    ?: UserProfile(id = user.id, displayName = editNameInput)
                                Toast.makeText(context, "Name updated!", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                e.printStackTrace()
                                Toast.makeText(context, "DB Error: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    showEditNameDialog = false
                }, colors = ButtonDefaults.buttonColors(containerColor = NexiRed)) {
                    Text("Save", fontFamily = InterFont, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditNameDialog = false }) {
                    Text("Cancel", color = themeTextSecondary(), fontFamily = InterFont)
                }
            },
            containerColor = themeSurface(),
            titleContentColor = themeTextPrimary(),
            textContentColor = themeTextPrimary()
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(themeBg())
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.height(16.dp))

        if (loading) {
            // ── Shimmer Loading Skeleton ──
            ProfileShimmerSkeleton()
        } else {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { 30 },
            ) {
                Column {
                    if (!isLoggedIn) {
                        // Guest state
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier = Modifier.size(80.dp).clip(CircleShape).background(themeCard()),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Default.Person, null,
                                    tint = themeTextTertiary(),
                                    modifier = Modifier.size(40.dp),
                                )
                            }

                            Spacer(Modifier.height(16.dp))
                            Text(
                                "Guest User",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = InterFont,
                                color = themeTextPrimary(),
                            )
                            Text(
                                "Login to access all features",
                                fontSize = 13.sp,
                                color = themeTextSecondary(),
                            )
                            Spacer(Modifier.height(20.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(
                                    onClick = { navController.navigate(Screen.Login.route) },
                                    colors = ButtonDefaults.buttonColors(containerColor = NexiRed),
                                    shape = RoundedCornerShape(12.dp),
                                ) { Text("Login", fontWeight = FontWeight.Bold, fontFamily = InterFont) }
                                OutlinedButton(
                                    onClick = { navController.navigate(Screen.Register.route) },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NexiRed),
                                ) { Text("Register", fontWeight = FontWeight.Bold, fontFamily = InterFont) }
                            }
                        }
                    } else {
                        // Logged in profile header
                        EliteProfileHeader(
                            profile = profile, 
                            onEditAvatar = { imagePicker.launch("image/*") },
                            onEditName = {
                                editNameInput = profile?.displayName ?: ""
                                showEditNameDialog = true
                            },
                            isUploading = isUploading
                        )

                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider(color = themeBorder(), modifier = Modifier.padding(horizontal = 16.dp))
                        Spacer(Modifier.height(8.dp))
                    }

                    // ── Menu Items ──
                    if (isLeaderboardEnabled) {
                        ProfileMenuItem(Icons.Default.EmojiEvents, "Leaderboard", "Top 15 ranking") {
                            navController.navigate(Screen.Leaderboard.route)
                        }
                    }

                    if (isLoggedIn) {
                        ProfileMenuItem(Icons.Default.Bookmark, "Watchlist", "Your saved content") {
                            navController.navigate(Screen.Watchlist.route)
                        }
                        ProfileMenuItem(Icons.Default.FileDownload, "My Downloads", "Downloaded offline files") {
                            navController.navigate("downloads")
                        }
                        ProfileMenuItem(Icons.Default.History, "Watch History", "Recently watched") {
                            navController.navigate("history")
                        }
                        ProfileMenuItem(Icons.Default.Comment, "My Comments", "Your comments") {
                            navController.navigate("comments")
                        }

                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider(color = themeBorder(), modifier = Modifier.padding(horizontal = 16.dp))
                        Spacer(Modifier.height(8.dp))
                    }

                    // ── Day/Night Toggle Row ──
                    DayNightToggleRow()

                    ProfileMenuItem(Icons.Default.Settings, "Settings", "App preferences") {
                        navController.navigate(Screen.Settings.route)
                    }

                    ProfileMenuItem(Icons.Default.SupportAgent, "Contact Us / Bug Report", "Report issues or contact support") {
                        navController.navigate(Screen.Contact.route)
                    }

                    if (isLoggedIn) {
                        Spacer(Modifier.height(8.dp))
                        // Logout
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .bounceClick(onClick = {
                                    scope.launch {
                                        SupabaseClient.main.auth.signOut()
                                        navController.navigate(Screen.Home.route) {
                                            popUpTo(0) { inclusive = true }
                                        }
                                    }
                                })
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ExitToApp, null,
                                tint = NexiRed,
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(Modifier.width(14.dp))
                            Text(
                                "Logout",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = InterFont,
                                color = NexiRed,
                            )
                        }
                    }

                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

// ── Elite Profile Header (pixel-perfect match to HTML reference) ──
@Composable
private fun EliteProfileHeader(profile: UserProfile?, onEditAvatar: () -> Unit, onEditName: () -> Unit, isUploading: Boolean) {
    val isElite = profile?.vipBadge == "gold_vip" || profile?.vipBadge == "elite_pro"
    val isVip = isElite || profile?.vipBadge == "vip"

    val infiniteTransition = rememberInfiniteTransition(label = "vip_anim")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp, start = 20.dp, end = 20.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ─── AVATAR AREA (160dp to fit glow + satellites outside 120dp ring) ───
        Box(
            modifier = Modifier
                .size(160.dp)
                .bounceClick(onClick = onEditAvatar),
            contentAlignment = Alignment.Center
        ) {
            // ═══ LAYER 1: BREATHING GLOW (behind everything) ═══
            // CSS: .ring-glow / .ring-pulse — radial-gradient, scale+opacity breathing
            if (isElite || isVip) {
                val glowScale by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = if (isElite) 1.12f else 1.1f,
                    animationSpec = infiniteRepeatable(
                        tween(if (isElite) 1000 else 1250, easing = androidx.compose.animation.core.EaseInOut),
                        RepeatMode.Reverse
                    ),
                    label = "glowS"
                )
                val glowAlpha by infiniteTransition.animateFloat(
                    initialValue = if (isElite) 0.5f else 0.4f,
                    targetValue = if (isElite) 0.85f else 0.75f,
                    animationSpec = infiniteRepeatable(
                        tween(if (isElite) 1000 else 1250, easing = androidx.compose.animation.core.EaseInOut),
                        RepeatMode.Reverse
                    ),
                    label = "glowA"
                )
                val glowBrush = if (isElite) {
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFA78BFA).copy(alpha = glowAlpha),
                            Color(0xFF7EE8FA).copy(alpha = glowAlpha * 0.6f),
                            Color.Transparent
                        )
                    )
                } else {
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFF6C453).copy(alpha = glowAlpha),
                            Color.Transparent
                        )
                    )
                }
                Box(
                    modifier = Modifier
                        .size(158.dp)
                        .scale(glowScale)
                        .background(glowBrush, CircleShape)
                )
            }

            // ═══ LAYER 2: DROP-SHADOW for ring (colored blur behind ring) ═══
            // CSS: filter:drop-shadow(0 0 14px rgba(246,196,83,.6))
            // Android shadow() doesn't do colored glow, so we use a blurred colored circle
            if (isElite || isVip) {
                val dropShadowColor = if (isElite) Color(0xFFA78BFA) else Color(0xFFF6C453)
                val dropShadowAlpha = if (isElite) 0.8f else 0.6f
                Box(
                    modifier = Modifier
                        .size(if (isElite) 140.dp else 134.dp)
                        .alpha(dropShadowAlpha)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    dropShadowColor.copy(alpha = if (isElite) 0.85f else 0.7f),
                                    dropShadowColor.copy(alpha = if (isElite) 0.45f else 0.3f),
                                    Color.Transparent
                                ),
                                radius = if (isElite) 240f else 200f
                            ),
                            CircleShape
                        )
                )
            }

            // ═══ LAYER 3: ROTATING RING (filled circle + inner mask) ═══
            // CSS: .avatar-ring::before (conic-gradient) + ::after (inset:4px mask)
            val ringAngle by infiniteTransition.animateFloat(
                initialValue = 0f, targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    tween(if (isElite) 3000 else if (isVip) 4500 else 6000, easing = LinearEasing)
                ),
                label = "ringA"
            )
            val ringColors = if (isElite) {
                listOf(Color(0xFF7EE8FA), Color(0xFFA78BFA), Color(0xFFF472B6), Color.White, Color(0xFF7EE8FA))
            } else if (isVip) {
                listOf(Color(0xFFF6C453), Color(0xFFFFF3C4), Color(0xFFB8860B), Color(0xFFFFE9A8), Color(0xFFF6C453))
            } else {
                listOf(NexiRed, NexiGradientEnd, NexiRedLight, GoldVip, NexiRed)
            }

            Box(
                modifier = Modifier
                    .size(120.dp)
                    .rotate(ringAngle)
                    .background(Brush.sweepGradient(ringColors), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                // Inner mask — CSS: .avatar-ring::after { inset:4px; background:app-bg }
                Box(
                    modifier = Modifier
                        .size(112.dp)
                        .background(themeBg(), CircleShape)
                )
            }

            // ═══ LAYER 4: ORBITING SATELLITES ═══
            // CSS: .satellite — rotate(N) translateX(68px) rotate(-N)
            if (isElite || isVip) {
                val satAngle by infiniteTransition.animateFloat(
                    initialValue = 0f, targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        tween(if (isElite) 3500 else 5000, easing = LinearEasing)
                    ),
                    label = "satA"
                )
                val satColors = if (isElite)
                    listOf(Color(0xFF7EE8FA), Color(0xFFA78BFA), Color(0xFFF472B6))
                else
                    listOf(Color(0xFFF6C453), Color(0xFFF6C453), Color(0xFFF6C453))
                val satSize = if (isElite) 5.dp else 6.dp
                val satGlowSize = if (isElite) 16.dp else 14.dp

                for (i in 0 until 3) {
                    // Each satellite orbits at 120° offset
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .rotate(satAngle + (i * 120f))
                    ) {
                        // Satellite glow (colored blur behind the dot)
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .offset(y = (80.dp - 68.dp - (satGlowSize / 2)))
                                .size(satGlowSize)
                                .alpha(0.5f)
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(satColors[i].copy(alpha = 0.7f), Color.Transparent)
                                    ),
                                    CircleShape
                                )
                        )
                        // Satellite dot
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .offset(y = (80.dp - 68.dp - (satSize / 2)))
                                .size(satSize)
                                .background(satColors[i], CircleShape)
                        )
                    }
                }

                // ═══ LAYER 5: SPARKLES (Elite only) ═══
                // CSS: .sparkles i — twinkle animation at fixed positions
                if (isElite) {
                    val sparkAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.2f, targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            tween(2200, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                            RepeatMode.Reverse
                        ),
                        label = "spkA"
                    )
                    val sparkScale by infiniteTransition.animateFloat(
                        initialValue = 0.4f, targetValue = 1.25f,
                        animationSpec = infiniteRepeatable(
                            tween(2200, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                            RepeatMode.Reverse
                        ),
                        label = "spkS"
                    )
                    // CSS positions: top:4% left:22%, top:16% right:4%, bottom:8% left:2%, bottom:2% right:16%
                    // In 160dp space (sparkle area = inset:-16px from 120px = 152px total, we use 160dp)
                    data class Spk(val xDp: Float, val yDp: Float, val c: Color, val phase: Int)
                    val spks = listOf(
                        Spk(35f, 6f, Color(0xFF7EE8FA), 0),   // top:4% left:22%
                        Spk(147f, 26f, Color(0xFFF472B6), 1),  // top:16% right:4%
                        Spk(3f, 131f, Color(0xFFA78BFA), 1),   // bottom:8% left:2%
                        Spk(131f, 150f, Color.White, 0)         // bottom:2% right:16%
                    )
                    spks.forEach { s ->
                        Box(
                            modifier = Modifier
                                .size(160.dp)
                        ) {
                            // Sparkle glow
                            Box(
                                modifier = Modifier
                                    .offset(x = (s.xDp - 4).dp, y = (s.yDp - 4).dp)
                                    .size(13.dp)
                                    .scale(if (s.phase == 0) sparkScale else (1.65f - sparkScale))
                                    .alpha((if (s.phase == 0) sparkAlpha else (1.2f - sparkAlpha)) * 0.5f)
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(s.c.copy(alpha = 0.7f), Color.Transparent)
                                        ),
                                        CircleShape
                                    )
                            )
                            // Sparkle dot
                            Box(
                                modifier = Modifier
                                    .offset(x = s.xDp.dp, y = s.yDp.dp)
                                    .size(5.dp)
                                    .scale(if (s.phase == 0) sparkScale else (1.65f - sparkScale))
                                    .alpha(if (s.phase == 0) sparkAlpha else (1.2f - sparkAlpha))
                                    .background(s.c, CircleShape)
                            )
                        }
                    }
                }

                // ═══ LAYER 6: TEXT OVERLAY ("VIP" / "PRO") ═══
                // CSS: .vip-text-overlay — letter-by-letter rise with cubic-bezier(.2,1.4,.4,1) overshoot
                // + vipShimmer continuous glow on text-shadow
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = 6.dp)
                ) {
                    val textStr = if (isElite) "PRO" else "VIP"
                    val textColor = if (isElite) null else Color(0xFFFFD666) // brighter gold
                    val textBrush = if (isElite) Brush.linearGradient(listOf(Color(0xFF7EE8FA), Color(0xFFF472B6))) else null

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        textStr.forEachIndexed { index, char ->
                            // Animatable allows staggered delay + spring overshoot
                            val yOff = remember { androidx.compose.animation.core.Animatable(16f) }
                            val lAlpha = remember { androidx.compose.animation.core.Animatable(0f) }

                            LaunchedEffect(Unit) {
                                kotlinx.coroutines.delay(index * 180L) // V=0ms, I=180ms, P=360ms
                                launch {
                                    yOff.animateTo(
                                        0f,
                                        androidx.compose.animation.core.spring(
                                            dampingRatio = 0.5f,
                                            stiffness = 300f
                                        )
                                    )
                                }
                                launch {
                                    lAlpha.animateTo(1f, tween(400))
                                }
                            }

                            val style = androidx.compose.ui.text.TextStyle(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.5.sp
                            ).let { base ->
                                if (textBrush != null) base.copy(brush = textBrush)
                                else base.copy(color = textColor ?: Color.White)
                            }

                            Text(
                                text = char.toString(),
                                style = style,
                                modifier = Modifier
                                    .offset(y = yOff.value.dp)
                                    .alpha(lAlpha.value)
                            )
                        }
                    }
                }
            }

            // ═══ LAYER 7: INNER AVATAR ═══
            // CSS: .avatar { inset:9px } → 120 - 2*9 = 102dp
            Box(
                modifier = Modifier
                    .size(102.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(0xFFC026D3), Color(0xFF7C3AED)))),
                contentAlignment = Alignment.Center
            ) {
                if (profile?.avatarUrl != null) {
                    AsyncImage(
                        profile.avatarUrl, "Avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        text = (profile?.displayName?.firstOrNull() ?: 'U').uppercase(),
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                    )
                }
                if (isUploading) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
            }

            // ═══ EDIT DOT ═══
            // CSS: .edit-dot { bottom:2px; right:2px } (relative to 120px avatar-box)
            // In 160dp parent, avatar is centered, so bottom-right of avatar is at offset
            if (!isUploading) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = (-16).dp, y = (-16).dp)
                        .size(26.dp)
                        .background(Color(0xFF26262F), CircleShape)
                        .border(2.5.dp, themeBg(), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Edit, "Edit", tint = Color(0xFFC9C9D8), modifier = Modifier.size(12.dp))
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // ─── NAME ROW + BADGE ───
        // CSS: .p-namerow { gap:10px } — name(pen) + badge inline
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.bounceClick(onClick = onEditName)
            ) {
                Text(
                    text = profile?.displayName ?: "User",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = themeTextPrimary(),
                    fontFamily = InterFont
                )
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Default.Edit, "Edit Name", tint = Color(0xFF6D6D82), modifier = Modifier.size(14.dp))
            }

            // ═══ BADGE PILL ═══
            // CSS: .badge.vip / .badge.elite — gradient bg, box-shadow, badgePop animation, shine sweep
            if (isElite || isVip) {
                Spacer(Modifier.width(10.dp))

                val pillBg = if (isElite) {
                    Brush.linearGradient(listOf(Color(0xFF7EE8FA), Color(0xFFA78BFA), Color(0xFFF472B6)))
                } else {
                    Brush.linearGradient(listOf(Color(0xFFAA7B0A), Color(0xFFFFD666), Color(0xFFAA7B0A)))
                }
                val pillShadowColor = if (isElite) Color(0xFFA78BFA) else Color(0xFFF6C453)
                val pillFg = if (isElite) Color(0xFF150826) else Color(0xFF1C1300)
                val pillText = if (isElite) "ELITE" else "VIP"
                val pillIcon = if (isElite) Icons.Default.Star else Icons.Default.WorkspacePremium

                // CSS: badgePop .5s cubic-bezier(.2,1.6,.4,1) — spring overshoot
                var popped by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { popped = true }
                val popScale by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (popped) 1f else 0.4f,
                    animationSpec = androidx.compose.animation.core.spring(
                        dampingRatio = 0.45f,
                        stiffness = 400f
                    ),
                    label = "pop"
                )
                val popAlpha by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (popped) 1f else 0f,
                    animationSpec = tween(400),
                    label = "popA"
                )

                // CSS: .badge::after shine sweep — left:-70% to left:140%
                val shinePos by infiniteTransition.animateFloat(
                    initialValue = -0.7f, targetValue = 1.4f,
                    animationSpec = infiniteRepeatable(
                        tween(2800, easing = androidx.compose.animation.core.EaseInOut)
                    ),
                    label = "shine"
                )

                Box(
                    modifier = Modifier
                        .scale(popScale)
                        .alpha(popAlpha)
                ) {
                    // Colored glow behind pill (CSS box-shadow: 0 3px 14px rgba(246,196,83,.45))
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .offset(y = 3.dp)
                            .alpha(if (isElite) 0.5f else 0.45f)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(pillShadowColor, Color.Transparent),
                                    radius = 250f
                                ),
                                RoundedCornerShape(999.dp)
                            )
                    )
                    // Pill with shine sweep
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(pillBg)
                            .padding(start = 8.dp, end = 12.dp, top = 5.dp, bottom = 5.dp)
                    ) {
                        // Shine sweep overlay (CSS: .badge::after — skewX(-20deg) italic shine)
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .graphicsLayer {
                                    translationX = shinePos * size.width
                                    rotationZ = 20f  // CSS: skewX(-20deg) — top-to-bottom slant
                                    scaleX = 0.45f   // CSS: width:45%
                                }
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.75f), Color.Transparent)
                                    )
                                )
                        )
                        // Badge icon + text
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(pillIcon, "Badge", tint = pillFg, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                pillText,
                                color = pillFg,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.2.sp
                            )
                        }
                    }
                }
            }
        }

        // Email
        Spacer(Modifier.height(6.dp))
        Text(
            text = profile?.email ?: "",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF8B8B9E)
        )

        // Elite tagline
        if (isElite) {
            Spacer(Modifier.height(6.dp))
            Text(
                "✦ Top 1% member · Founding Elite ✦",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                style = androidx.compose.ui.text.TextStyle(
                    brush = Brush.linearGradient(listOf(Color(0xFF7EE8FA), Color(0xFFF472B6)))
                )
            )
        }

        if (profile?.referralCode != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Referral: ${profile.referralCode}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF6C453),
                letterSpacing = 0.3.sp
            )
        }
    }
}

// ── Day / Night Toggle Row ──
@Composable
private fun DayNightToggleRow() {
    val dark = isDark
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (dark) Icons.Default.DarkMode else Icons.Default.LightMode,
            contentDescription = null,
            tint = if (dark) GoldVip else WarningYellow,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (dark) "Dark Mode" else "Light Mode",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = InterFont,
                color = themeTextPrimary(),
            )
            Text(
                "Toggle appearance",
                fontSize = 11.sp,
                color = themeTextTertiary(),
            )
        }
        Switch(
            checked = dark,
            onCheckedChange = { ThemeState.isDarkMode = it },
            colors = SwitchDefaults.colors(
                checkedThumbColor = NexiRed,
                checkedTrackColor = NexiRed.copy(alpha = 0.3f),
                uncheckedThumbColor = themeTextSecondary(),
                uncheckedTrackColor = themeBorder(),
            ),
        )
    }
}

// ── Shimmer Loading Skeleton ──
@Composable
private fun ProfileShimmerSkeleton() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Avatar placeholder
        ShimmerBox(modifier = Modifier.size(68.dp).clip(CircleShape), cornerRadius = 34)
        Spacer(Modifier.height(16.dp))
        ShimmerBox(modifier = Modifier.width(140.dp).height(18.dp))
        Spacer(Modifier.height(8.dp))
        ShimmerBox(modifier = Modifier.width(200.dp).height(12.dp))
        Spacer(Modifier.height(24.dp))
        // Menu item placeholders
        repeat(5) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ShimmerBox(modifier = Modifier.size(24.dp), cornerRadius = 6)
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    ShimmerBox(modifier = Modifier.fillMaxWidth(0.4f).height(14.dp))
                    Spacer(Modifier.height(4.dp))
                    ShimmerBox(modifier = Modifier.fillMaxWidth(0.6f).height(10.dp))
                }
            }
        }
    }
}

// ── Menu Item ──
@Composable
private fun ProfileMenuItem(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = themeTextSecondary(), modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = InterFont,
                color = themeTextPrimary(),
            )
            Text(subtitle, fontSize = 11.sp, color = themeTextTertiary())
        }
        Icon(Icons.Default.ChevronRight, null, tint = themeTextTertiary(), modifier = Modifier.size(20.dp))
    }
}
