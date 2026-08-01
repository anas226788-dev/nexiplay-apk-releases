package com.nexiplay.app.ui.screens.leaderboard

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.nexiplay.app.data.SupabaseClient
import com.nexiplay.app.data.model.LeaderboardEntry
import com.nexiplay.app.data.model.UserProfile
import com.nexiplay.app.data.model.CoinBalance
import com.nexiplay.app.ui.theme.*
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

private val GoldRankColor = Color(0xFFFFD700)
private val SilverRankColor = Color(0xFFE0E0E0)
private val BronzeRankColor = Color(0xFFCD7F32)

@Composable
fun LeaderboardScreen(navController: NavController) {
    var entries by remember { mutableStateOf<List<LeaderboardEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isLeaderboardEnabled by remember { mutableStateOf(true) }
    
    val currentUser = SupabaseClient.main.auth.currentUserOrNull()
    val currentUserId = currentUser?.id

    LaunchedEffect(Unit) {
        // Check if leaderboard is enabled in app_settings
        try {
            val json = SupabaseClient.main.from("app_settings")
                .select(Columns.list("is_leaderboard_enabled")) { filter { eq("id", 1) } }
                .decodeSingleOrNull<kotlinx.serialization.json.JsonObject>()
            val enabled = json?.get("is_leaderboard_enabled")?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
            if (enabled == false) {
                isLeaderboardEnabled = false
                isLoading = false
                return@LaunchedEffect
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Fetch Real User Profile & Exact Coin Balance
        var realProfile: UserProfile? = null
        var realCoins = 0
        var realWatched = 0
        if (currentUserId != null) {
            try {
                realProfile = SupabaseClient.main.from("profiles")
                    .select(Columns.list(
                        "id", "email", "display_name", "avatar_url", "vip_badge", "vip_badge_expires"
                    )) { filter { eq("id", currentUserId) } }
                    .decodeSingleOrNull<UserProfile>()

                val metaAvatar = currentUser.userMetadata?.get("avatar_url")?.let { 
                    if (it is kotlinx.serialization.json.JsonPrimitive) it.contentOrNull else null 
                }
                val metaName = currentUser.userMetadata?.get("display_name")?.let { 
                    if (it is kotlinx.serialization.json.JsonPrimitive) it.contentOrNull else null 
                }

                if (realProfile != null) {
                    realProfile = realProfile.copy(
                        avatarUrl = realProfile.avatarUrl ?: metaAvatar,
                        displayName = realProfile.displayName ?: metaName ?: currentUser.email?.split("@")?.get(0) ?: "User"
                    )
                }

                // Fetch EXACT coin balance from coin_balances
                try {
                    val bal = SupabaseClient.main.from("coin_balances")
                        .select { filter { eq("user_id", currentUserId) } }
                        .decodeSingleOrNull<CoinBalance>()
                    if (bal != null) realCoins = bal.balance
                } catch (_: Exception) {}

                // Fetch watched count
                try {
                    val evList = SupabaseClient.main.from("user_events")
                        .select { filter { eq("user_id", currentUserId) } }
                        .decodeList<com.nexiplay.app.data.model.UserEvent>()
                    realWatched = evList.size
                } catch (_: Exception) {}
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        try {
            val list = SupabaseClient.main.from("leaderboard_entries")
                .select { order("rank", order = Order.ASCENDING) }
                .decodeList<LeaderboardEntry>()

            if (list.isNotEmpty()) {
                entries = list.map { e ->
                    if (e.userId == currentUserId && realProfile != null) {
                        e.copy(
                            avatarUrl = realProfile.avatarUrl ?: e.avatarUrl,
                            name = realProfile.displayName ?: e.name,
                            badgeType = realProfile.vipBadge ?: e.badgeType,
                            coins = realCoins,
                            watchedCount = realWatched
                        )
                    } else e
                }
            } else {
                entries = generateFallbackLeaderboard(realProfile, currentUserId, realCoins, realWatched)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            entries = generateFallbackLeaderboard(realProfile, currentUserId, realCoins, realWatched)
        } finally {
            isLoading = false
        }
    }

    val rank1 = entries.firstOrNull { it.rank == 1 }
    val rank2 = entries.firstOrNull { it.rank == 2 }
    val rank3 = entries.firstOrNull { it.rank == 3 }
    val lowerRanks = entries.filter { it.rank > 3 }

    Scaffold(
        containerColor = themeBg(),
        topBar = {
            Surface(
                color = themeBg(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                ) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = themeTextPrimary()
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Leaderboard",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = themeTextPrimary(),
                        fontFamily = InterFont
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            if (!isLeaderboardEnabled) {
                // Disabled State Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(themeSurface())
                        .border(1.dp, themeBorder(), RoundedCornerShape(20.dp))
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = null,
                            tint = themeTextTertiary(),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "Leaderboard Currently Offline",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = themeTextPrimary(),
                            fontFamily = InterFont
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "The leaderboard is undergoing maintenance or is temporarily disabled by admin.",
                            fontSize = 13.sp,
                            color = themeTextSecondary(),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = NexiRed)
                }
            } else {
                // Top 3 Podium Cards
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom
                ) {
                    // #2 Silver (Left)
                    rank2?.let {
                        PodiumCardItem(
                            entry = it,
                            rankColor = SilverRankColor,
                            crownText = "🥈",
                            avatarSize = 64.dp,
                            isWinner = false,
                            isCurrentUser = it.userId != null && it.userId == currentUserId,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // #1 Gold (Center, Elevated)
                    rank1?.let {
                        PodiumCardItem(
                            entry = it,
                            rankColor = GoldRankColor,
                            crownText = "👑",
                            avatarSize = 80.dp,
                            isWinner = true,
                            isCurrentUser = it.userId != null && it.userId == currentUserId,
                            modifier = Modifier.weight(1.2f)
                        )
                    }

                    // #3 Bronze (Right)
                    rank3?.let {
                        PodiumCardItem(
                            entry = it,
                            rankColor = BronzeRankColor,
                            crownText = "🥉",
                            avatarSize = 60.dp,
                            isWinner = false,
                            isCurrentUser = it.userId != null && it.userId == currentUserId,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(Modifier.height(28.dp))
                HorizontalDivider(color = themeBorder())
                Spacer(Modifier.height(16.dp))

                // Ranks 4 to 15 List
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    lowerRanks.forEach { entry ->
                        val isSelf = entry.userId != null && entry.userId == currentUserId
                        LeaderboardRowItem(entry = entry, isCurrentUser = isSelf)
                    }
                }

                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

/** Animated Avatar Ring (exact match to ProfileScreen EliteProfileHeader style) */
@Composable
fun AnimatedLeaderboardAvatar(
    avatarUrl: String?,
    name: String,
    badgeType: String,
    avatarSize: Dp,
    rank: Int,
    rankColor: Color,
    modifier: Modifier = Modifier
) {
    val badgeLower = badgeType.lowercase().trim()
    val isElite = badgeLower.contains("elite") || badgeLower == "gold_vip"
    val isVip = !isElite && badgeLower.contains("vip")

    val infiniteTransition = rememberInfiniteTransition(label = "lb_ring_anim")
    val totalContainerSize = avatarSize + 28.dp

    Box(
        modifier = modifier.size(totalContainerSize),
        contentAlignment = Alignment.Center
    ) {
        // ═══ LAYER 1: BREATHING GLOW ═══
        if (isElite || isVip) {
            val glowScale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = if (isElite) 1.12f else 1.08f,
                animationSpec = infiniteRepeatable(
                    tween(if (isElite) 1000 else 1250, easing = EaseInOut),
                    RepeatMode.Reverse
                ),
                label = "lb_glowS"
            )
            val glowAlpha by infiniteTransition.animateFloat(
                initialValue = if (isElite) 0.5f else 0.4f,
                targetValue = if (isElite) 0.85f else 0.75f,
                animationSpec = infiniteRepeatable(
                    tween(if (isElite) 1000 else 1250, easing = EaseInOut),
                    RepeatMode.Reverse
                ),
                label = "lb_glowA"
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
                    .size(totalContainerSize - 4.dp)
                    .scale(glowScale)
                    .background(glowBrush, CircleShape)
            )
        }

        // ═══ LAYER 2: ROTATING SWEEP GRADIENT RING ═══
        val ringAngle by infiniteTransition.animateFloat(
            initialValue = 0f, targetValue = 360f,
            animationSpec = infiniteRepeatable(
                tween(if (isElite) 3000 else if (isVip) 4500 else 6000, easing = LinearEasing)
            ),
            label = "lb_ringA"
        )
        val ringColors = if (isElite) {
            listOf(Color(0xFF7EE8FA), Color(0xFFA78BFA), Color(0xFFF472B6), Color.White, Color(0xFF7EE8FA))
        } else if (isVip) {
            listOf(Color(0xFFF6C453), Color(0xFFFFF3C4), Color(0xFFB8860B), Color(0xFFFFE9A8), Color(0xFFF6C453))
        } else {
            listOf(rankColor, rankColor.copy(alpha = 0.5f), rankColor)
        }

        Box(
            modifier = Modifier
                .size(avatarSize + 8.dp)
                .rotate(ringAngle)
                .background(Brush.sweepGradient(ringColors), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(avatarSize + 2.dp)
                    .background(themeBg(), CircleShape)
            )
        }

        // ═══ LAYER 3: ORBITING SATELLITES ═══
        if (isElite || isVip) {
            val satAngle by infiniteTransition.animateFloat(
                initialValue = 0f, targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    tween(if (isElite) 3500 else 5000, easing = LinearEasing)
                ),
                label = "lb_satA"
            )
            val satColors = if (isElite)
                listOf(Color(0xFF7EE8FA), Color(0xFFA78BFA), Color(0xFFF472B6))
            else
                listOf(Color(0xFFF6C453), Color(0xFFF6C453), Color(0xFFF6C453))
            val satSize = if (isElite) 4.dp else 5.dp
            val satRadius = (avatarSize + 8.dp) / 2

            for (i in 0 until 3) {
                Box(
                    modifier = Modifier
                        .size(totalContainerSize)
                        .rotate(satAngle + (i * 120f))
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = ((totalContainerSize / 2) - satRadius - (satSize / 2)))
                            .size(satSize)
                            .background(satColors[i], CircleShape)
                    )
                }
            }

            // ═══ SPARKLES (Elite only) ═══
            if (isElite) {
                val sparkAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.2f, targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        tween(2200, easing = FastOutSlowInEasing),
                        RepeatMode.Reverse
                    ),
                    label = "lb_spkA"
                )
                val sparkScale by infiniteTransition.animateFloat(
                    initialValue = 0.4f, targetValue = 1.25f,
                    animationSpec = infiniteRepeatable(
                        tween(2200, easing = FastOutSlowInEasing),
                        RepeatMode.Reverse
                    ),
                    label = "lb_spkS"
                )
                Box(modifier = Modifier.size(totalContainerSize)) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(x = 6.dp, y = 6.dp)
                            .size(4.dp)
                            .scale(sparkScale)
                            .alpha(sparkAlpha)
                            .background(Color(0xFF7EE8FA), CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = (-6).dp, y = (-6).dp)
                            .size(4.dp)
                            .scale(1.6f - sparkScale)
                            .alpha(1.2f - sparkAlpha)
                            .background(Color(0xFFF472B6), CircleShape)
                    )
                }
            }

            // ═══ TEXT OVERLAY ("VIP" / "PRO") ═══
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-4).dp)
            ) {
                val textStr = if (isElite) "PRO" else "VIP"
                val textColor = if (isElite) null else Color(0xFFFFD666)
                val textBrush = if (isElite) Brush.linearGradient(listOf(Color(0xFF7EE8FA), Color(0xFFF472B6))) else null

                Row(
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    textStr.forEachIndexed { idx, char ->
                        val yOff = remember { Animatable(12f) }
                        val lAlpha = remember { Animatable(0f) }

                        LaunchedEffect(Unit) {
                            delay(idx * 150L)
                            launch { yOff.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = 300f)) }
                            launch { lAlpha.animateTo(1f, tween(400)) }
                        }

                        val style = androidx.compose.ui.text.TextStyle(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        ).let { base ->
                            if (textBrush != null) base.copy(brush = textBrush)
                            else base.copy(color = textColor ?: Color.White)
                        }

                        Text(
                            text = char.toString(),
                            style = style,
                            modifier = Modifier.offset(y = yOff.value.dp).alpha(lAlpha.value)
                        )
                    }
                }
            }
        }

        // ═══ INNER AVATAR ═══
        Box(
            modifier = Modifier
                .size(avatarSize)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFFC026D3), Color(0xFF7C3AED))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            if (!avatarUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = name.take(1).uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = (avatarSize.value * 0.45f).sp
                )
            }
        }

        // ═══ RANK BADGE NUMBER ═══
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = 6.dp)
                .clip(CircleShape)
                .background(rankColor)
                .padding(horizontal = 7.dp, vertical = 1.dp)
        ) {
            Text(
                text = "#$rank",
                color = Color.Black,
                fontWeight = FontWeight.Black,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun PodiumCardItem(
    entry: LeaderboardEntry,
    rankColor: Color,
    crownText: String,
    avatarSize: Dp,
    isWinner: Boolean,
    isCurrentUser: Boolean,
    modifier: Modifier = Modifier
) {
    val badgeLower = entry.badgeType.lowercase().trim()
    val isElite = badgeLower.contains("elite") || badgeLower == "gold_vip"
    val isVip = !isElite && badgeLower.contains("vip")

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(horizontal = 2.dp)
    ) {
        Text(text = crownText, fontSize = if (isWinner) 26.sp else 20.sp)
        Spacer(Modifier.height(2.dp))

        AnimatedLeaderboardAvatar(
            avatarUrl = entry.avatarUrl,
            name = entry.name,
            badgeType = entry.badgeType,
            avatarSize = avatarSize,
            rank = entry.rank,
            rankColor = rankColor
        )

        Spacer(Modifier.height(14.dp))

        Text(
            text = if (isCurrentUser) "${entry.name} (YOU)" else entry.name,
            fontWeight = FontWeight.Bold,
            fontSize = if (isWinner) 13.sp else 12.sp,
            color = if (isCurrentUser) Color(0xFFFFD700) else themeTextPrimary(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(3.dp))

        if (isElite) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color.Transparent,
                modifier = Modifier.background(
                    Brush.linearGradient(listOf(Color(0xFF7EE8FA), Color(0xFFA855F7))),
                    RoundedCornerShape(4.dp)
                )
            ) {
                Text(
                    text = "💎 ELITE",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.Black,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
        } else if (isVip) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color(0xFFFFD700)
            ) {
                Text(
                    text = "👑 VIP",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.Black,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        Text(
            text = "${entry.coins} 🪙",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFFD700)
        )

        Text(
            text = "${entry.watchedCount} Watched",
            fontSize = 10.sp,
            color = themeTextTertiary()
        )
    }
}

@Composable
private fun LeaderboardRowItem(
    entry: LeaderboardEntry,
    isCurrentUser: Boolean
) {
    val badgeLower = entry.badgeType.lowercase().trim()
    val isElite = badgeLower.contains("elite") || badgeLower == "gold_vip"
    val isVip = !isElite && badgeLower.contains("vip")

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isCurrentUser) Color(0xFFFFD700).copy(alpha = 0.12f)
                else themeSurface()
            )
            .border(
                width = if (isCurrentUser) 1.dp else 0.5.dp,
                color = if (isCurrentUser) Color(0xFFFFD700).copy(alpha = 0.6f)
                else themeBorder(),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            text = "#${entry.rank}",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = if (isCurrentUser) Color(0xFFFFD700) else themeTextSecondary(),
            modifier = Modifier.width(32.dp)
        )

        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(
                    if (isElite) Brush.linearGradient(listOf(Color(0xFF7EE8FA), Color(0xFFA855F7)))
                    else if (isVip) Brush.linearGradient(listOf(Color(0xFFFFD700), Color(0xFFFFA500)))
                    else Brush.linearGradient(listOf(Color(0xFF2A2740), Color(0xFF1A1828)))
                )
                .padding(1.5.dp),
            contentAlignment = Alignment.Center
        ) {
            if (!entry.avatarUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = entry.avatarUrl,
                    contentDescription = entry.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(Color.Black)
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(Color(0xFF1F1D2B)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = entry.name.take(1).uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isCurrentUser) "${entry.name} (YOU)" else entry.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isCurrentUser) Color(0xFFFFD700) else themeTextPrimary(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (isElite) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                Brush.linearGradient(listOf(Color(0xFF7EE8FA), Color(0xFFA855F7)))
                            )
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text("💎 ELITE", fontSize = 8.sp, fontWeight = FontWeight.Black, color = Color.Black)
                    }
                } else if (isVip) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xFFFFD700))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text("👑 VIP", fontSize = 8.sp, fontWeight = FontWeight.Black, color = Color.Black)
                    }
                }
            }

            Text(
                text = "${entry.watchedCount} Content Watched",
                fontSize = 10.sp,
                color = themeTextTertiary()
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${entry.coins}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFFD700)
            )
            Spacer(Modifier.width(3.dp))
            Text(text = "🪙", fontSize = 12.sp)
        }
    }
}

/** Fallback generator using realistic lower coin values suitable for app launch */
private fun generateFallbackLeaderboard(
    realProfile: UserProfile?,
    currentUserId: String?,
    realCoins: Int,
    realWatched: Int
): List<LeaderboardEntry> {
    val names = listOf(
        realProfile?.displayName ?: "Nexi User",
        "Tanvir_Pro", "Siam_Vip", "Mahir_X", "Nibir_77",
        "Rafi_Hero", "Sabbir_Otaku", "Fahim_Stream", "Rayan_99", "Imran_VIP",
        "Tahmid_Ninja", "Nabil_Elite", "Hamza_Play", "Zayan_Legend", "Faris_Ultra"
    )
    val badges = listOf(
        realProfile?.vipBadge ?: "elite",
        "vip", "vip", "none", "none", "none", "none", "none", "none", "none", "none", "none", "none", "none", "none"
    )
    val animeAvatars = listOf(
        realProfile?.avatarUrl,
        "https://api.dicebear.com/7.x/bottts/png?seed=Tanvir",
        "https://api.dicebear.com/7.x/bottts/png?seed=Siam",
        "https://api.dicebear.com/7.x/bottts/png?seed=Mahir",
        "https://api.dicebear.com/7.x/bottts/png?seed=Nibir",
        "https://api.dicebear.com/7.x/bottts/png?seed=Rafi",
        "https://api.dicebear.com/7.x/bottts/png?seed=Sabbir",
        "https://api.dicebear.com/7.x/bottts/png?seed=Fahim",
        "https://api.dicebear.com/7.x/bottts/png?seed=Rayan",
        "https://api.dicebear.com/7.x/bottts/png?seed=Imran",
        "https://api.dicebear.com/7.x/bottts/png?seed=Tahmid",
        "https://api.dicebear.com/7.x/bottts/png?seed=Nabil",
        "https://api.dicebear.com/7.x/bottts/png?seed=Hamza",
        "https://api.dicebear.com/7.x/bottts/png?seed=Zayan",
        "https://api.dicebear.com/7.x/bottts/png?seed=Faris"
    )

    return List(15) { index ->
        val r = index + 1
        val isFirst = index == 0
        // Low realistic coins for newly launched app: 180, 160, 140... down to 10
        val simCoinVal = if (isFirst) realCoins else maxOf(8, 180 - (r * 11))
        val simWatchedVal = if (isFirst) realWatched else maxOf(1, 16 - r)

        LeaderboardEntry(
            id = "lb_$r",
            rank = r,
            userId = if (isFirst) currentUserId else null,
            name = if (isFirst && realProfile?.displayName != null) realProfile.displayName else names[index],
            avatarUrl = if (isFirst) realProfile?.avatarUrl else animeAvatars[index],
            badgeType = if (isFirst && realProfile?.vipBadge != null) realProfile.vipBadge else badges[index],
            coins = simCoinVal,
            watchedCount = simWatchedVal,
            isFake = !isFirst
        )
    }
}
