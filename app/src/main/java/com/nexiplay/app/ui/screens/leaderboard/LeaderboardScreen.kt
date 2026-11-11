package com.nexiplay.app.ui.screens.leaderboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.nexiplay.app.data.model.CoinBalance
import com.nexiplay.app.data.model.LeaderboardEntry
import com.nexiplay.app.data.model.UserEvent
import com.nexiplay.app.data.model.UserProfile
import com.nexiplay.app.data.util.AdManager
import com.nexiplay.app.ui.components.ShimmerBox
import com.nexiplay.app.ui.components.bounceClick
import com.nexiplay.app.ui.navigation.Screen
import com.nexiplay.app.ui.theme.*
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.text.NumberFormat
import java.util.Locale

private val GoldRankColor = Color(0xFFFFC978)
private val GoldRankDeep = Color(0xFFD98A3A)
private val SilverRankColor = Color(0xFFE2E8F0)
private val SilverRankDeep = Color(0xFF94A3B8)
private val BronzeRankColor = Color(0xFFF5A97F)
private val BronzeRankDeep = Color(0xFFB45309)
private val EliteCyan = Color(0xFF7EE8FA)
private val EliteViolet = Color(0xFF9273F5)
private val ElitePink = Color(0xFFF472B6)

private fun formatCoinCount(coins: Int): String =
    NumberFormat.getIntegerInstance(Locale.US).format(coins)

@Composable
fun LeaderboardScreen(navController: NavController) {
    var entries by remember { mutableStateOf<List<LeaderboardEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var isLeaderboardEnabled by remember { mutableStateOf(true) }
    var showRulesDialog by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    var selfProfile by remember { mutableStateOf<UserProfile?>(null) }
    var selfCoins by remember { mutableIntStateOf(0) }
    var selfWatched by remember { mutableIntStateOf(0) }

    val currentUser = SupabaseClient.main.auth.currentUserOrNull()
    val currentUserId = currentUser?.id

    LaunchedEffect(refreshTrigger) {
        if (refreshTrigger > 0) isRefreshing = true else isLoading = true

        try {
            val json = SupabaseClient.main.from("app_settings")
                .select(Columns.list("is_leaderboard_enabled")) { filter { eq("id", 1) } }
                .decodeSingleOrNull<JsonObject>()
            val enabled = json?.get("is_leaderboard_enabled")?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
            if (enabled == false) {
                isLeaderboardEnabled = false
                isLoading = false
                isRefreshing = false
                return@LaunchedEffect
            }
            isLeaderboardEnabled = true
        } catch (_: Exception) {}

        var realProfile: UserProfile? = null
        var realCoins = 0
        var realWatched = 0
        if (currentUserId != null) {
            try {
                realProfile = SupabaseClient.main.from("profiles")
                    .select(
                        Columns.list(
                            "id", "email", "display_name", "avatar_url", "vip_badge", "vip_badge_expires"
                        )
                    ) { filter { eq("id", currentUserId) } }
                    .decodeSingleOrNull<UserProfile>()

                val metaAvatar = currentUser.userMetadata?.get("avatar_url")?.let {
                    if (it is JsonPrimitive) it.contentOrNull else null
                }
                val metaName = currentUser.userMetadata?.get("display_name")?.let {
                    if (it is JsonPrimitive) it.contentOrNull else null
                }

                if (realProfile != null) {
                    realProfile = realProfile.copy(
                        avatarUrl = realProfile.avatarUrl ?: metaAvatar,
                        displayName = realProfile.displayName ?: metaName ?: currentUser.email?.split("@")?.get(0) ?: "User"
                    )
                }

                try {
                    val bal = SupabaseClient.main.from("coin_balances")
                        .select { filter { eq("user_id", currentUserId) } }
                        .decodeSingleOrNull<CoinBalance>()
                    if (bal != null) realCoins = bal.balance
                } catch (_: Exception) {}

                try {
                    val evList = SupabaseClient.main.from("user_events")
                        .select {
                            filter {
                                eq("user_id", currentUserId)
                                eq("event_type", "watch")
                            }
                        }
                        .decodeList<UserEvent>()
                    realWatched = evList.size
                } catch (_: Exception) {}
            } catch (_: Exception) {}
        }

        selfProfile = realProfile
        selfCoins = realCoins
        selfWatched = realWatched

        try {
            val rawList = SupabaseClient.main.from("leaderboard_entries")
                .select {
                    filter { eq("is_fake", false) }
                    order("rank", order = Order.ASCENDING)
                }
                .decodeList<LeaderboardEntry>()
                .filter { !it.isFake }
                .distinctBy { it.userId ?: "rank_${it.rank}" }
                .distinctBy { it.rank }

            if (rawList.isNotEmpty()) {
                val activeBadge = realProfile?.vipBadge?.takeIf {
                    val exp = AdManager.parseDateRobust(realProfile.vipBadgeExpires)
                    exp == null || exp.isAfter(java.time.Instant.now())
                }
                entries = rawList.mapIndexed { idx, e ->
                    val normalizedRank = idx + 1
                    if (e.userId == currentUserId && realProfile != null) {
                        e.copy(
                            rank = normalizedRank,
                            avatarUrl = realProfile.avatarUrl ?: e.avatarUrl,
                            name = realProfile.displayName ?: e.name,
                            badgeType = activeBadge ?: e.badgeType,
                            coins = maxOf(realCoins, e.coins),
                            watchedCount = maxOf(realWatched, e.watchedCount),
                            isFake = false
                        )
                    } else {
                        e.copy(rank = normalizedRank, isFake = false)
                    }
                }
            } else {
                entries = fetchRealUsersFallback(realProfile, currentUserId, realCoins, realWatched)
            }
        } catch (_: Exception) {
            entries = fetchRealUsersFallback(realProfile, currentUserId, realCoins, realWatched)
        } finally {
            isLoading = false
            isRefreshing = false
        }
    }

    val rank1 = entries.firstOrNull { it.rank == 1 }
    val rank2 = entries.firstOrNull { it.rank == 2 }
    val rank3 = entries.firstOrNull { it.rank == 3 }
    val lowerRanks = entries.filter { it.rank > 3 }
    val selfEntry = remember(entries, currentUserId) {
        if (currentUserId == null) null else entries.firstOrNull { it.userId == currentUserId }
    }

    if (showRulesDialog) {
        AlertDialog(
            onDismissRequest = { showRulesDialog = false },
            containerColor = themeCard(),
            shape = RoundedCornerShape(22.dp),
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Brush.linearGradient(listOf(GoldRankColor, GoldRankDeep))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.EmojiEvents, contentDescription = null, tint = Color.Black, modifier = Modifier.size(19.dp))
                    }
                    Text(
                        "Hall of Fame Rules",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = themeTextPrimary(),
                        fontFamily = InterFont
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Rankings feature verified NexiPlay community members ordered by tier prestige and activity:",
                        fontSize = 13.sp,
                        color = themeTextSecondary(),
                        lineHeight = 19.sp,
                        fontFamily = InterFont
                    )
                    RuleTierRow("1. Elite Pro Members", "Cyan-violet diamond priority tier", EliteCyan)
                    RuleTierRow("2. VIP Members", "Champagne gold crown priority tier", GoldRankColor)
                    RuleTierRow("3. Coin Balance", "Total NexiCoins earned & held", CoinGold)
                    RuleTierRow("4. Watch Activity", "Tie-breaker based on total watched", SuccessGreen)
                }
            },
            confirmButton = {
                TextButton(onClick = { showRulesDialog = false }) {
                    Text("Got It", color = NexiRed, fontWeight = FontWeight.Bold, fontFamily = InterFont)
                }
            }
        )
    }

    Scaffold(
        containerColor = themeBg(),
        topBar = {
            PremiumLeaderboardTopBar(
                isRefreshing = isRefreshing,
                onBack = { navController.popBackStack() },
                onRefresh = { if (!isLoading && !isRefreshing) refreshTrigger++ },
                onOpenRules = { showRulesDialog = true }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Subtle atmospheric mesh glow behind podium
            if (isLeaderboardEnabled && !isLoading && entries.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    GoldRankColor.copy(alpha = 0.14f),
                                    NexiRed.copy(alpha = 0.10f),
                                    Color.Transparent
                                ),
                                radius = 620f
                            )
                        )
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                if (!isLeaderboardEnabled) {
                    OfflineLeaderboardState()
                } else if (isLoading) {
                    LeaderboardLoadingSkeleton()
                } else if (entries.isEmpty()) {
                    EmptyLeaderboardState(
                        onEarnClick = { navController.navigate(Screen.Coins.route) }
                    )
                } else {
                    // ── Hero Top 3 Tiered Glassmorphic Podium ──
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(26.dp))
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        themeSurface().copy(alpha = 0.72f),
                                        themeCard().copy(alpha = 0.94f)
                                    )
                                )
                            )
                            .border(
                                width = 1.dp,
                                brush = Brush.verticalGradient(
                                    listOf(
                                        GoldRankColor.copy(alpha = 0.36f),
                                        NexiRed.copy(alpha = 0.18f),
                                        themeBorder()
                                    )
                                ),
                                shape = RoundedCornerShape(26.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            // #2 Silver (Left)
                            if (rank2 != null) {
                                PodiumStageColumn(
                                    entry = rank2,
                                    accentColor = SilverRankColor,
                                    accentDeep = SilverRankDeep,
                                    avatarSize = 56.dp,
                                    pedestalHeight = 112.dp,
                                    isWinner = false,
                                    isCurrentUser = rank2.userId != null && rank2.userId == currentUserId,
                                    modifier = Modifier.weight(1f)
                                )
                            } else if (rank1 != null && rank3 == null) {
                                Spacer(Modifier.weight(0.5f))
                            }

                            // #1 Gold (Center, Elevated)
                            if (rank1 != null) {
                                PodiumStageColumn(
                                    entry = rank1,
                                    accentColor = GoldRankColor,
                                    accentDeep = GoldRankDeep,
                                    avatarSize = 72.dp,
                                    pedestalHeight = 134.dp,
                                    isWinner = true,
                                    isCurrentUser = rank1.userId != null && rank1.userId == currentUserId,
                                    modifier = Modifier.weight(1.16f)
                                )
                            }

                            // #3 Bronze (Right)
                            if (rank3 != null) {
                                PodiumStageColumn(
                                    entry = rank3,
                                    accentColor = BronzeRankColor,
                                    accentDeep = BronzeRankDeep,
                                    avatarSize = 54.dp,
                                    pedestalHeight = 102.dp,
                                    isWinner = false,
                                    isCurrentUser = rank3.userId != null && rank3.userId == currentUserId,
                                    modifier = Modifier.weight(1f)
                                )
                            } else if (rank1 != null && rank2 == null) {
                                Spacer(Modifier.weight(0.5f))
                            }
                        }
                    }

                    // ── Personal "Your Standing" Summary Card ──
                    if (currentUserId != null) {
                        Spacer(Modifier.height(14.dp))
                        YourStandingCard(
                            selfEntry = selfEntry,
                            fallbackProfile = selfProfile,
                            fallbackCoins = selfCoins,
                            fallbackWatched = selfWatched,
                            onBoostClick = { navController.navigate(Screen.Coins.route) }
                        )
                    }

                    // ── Contenders List (#4 to #15) ──
                    if (lowerRanks.isNotEmpty()) {
                        Spacer(Modifier.height(22.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(14.dp)
                                        .clip(CircleShape)
                                        .background(Brush.verticalGradient(listOf(GoldRankColor, NexiRed)))
                                )
                                Text(
                                    text = "TOP CONTENDERS",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.2.sp,
                                    color = themeTextSecondary(),
                                    fontFamily = InterFont
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(50),
                                color = themeSurface(),
                                border = BorderStroke(1.dp, themeBorder())
                            ) {
                                Text(
                                    text = "${entries.size} Ranked",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = themeTextSecondary(),
                                    fontFamily = InterFont,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            lowerRanks.forEachIndexed { index, entry ->
                                val isSelf = entry.userId != null && entry.userId == currentUserId
                                var visible by remember(entry.id) { mutableStateOf(false) }
                                LaunchedEffect(entry.id) {
                                    delay((index * 35L).coerceAtMost(280L))
                                    visible = true
                                }
                                AnimatedVisibility(
                                    visible = visible,
                                    enter = fadeIn(tween(240)) + slideInVertically(
                                        initialOffsetY = { it / 4 },
                                        animationSpec = tween(260)
                                    )
                                ) {
                                    LeaderboardRowItem(entry = entry, isCurrentUser = isSelf)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(36.dp))
                }
            }
        }
    }
}

@Composable
private fun PremiumLeaderboardTopBar(
    isRefreshing: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenRules: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "live_dot")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = EaseInOut), RepeatMode.Reverse),
        label = "dot_a"
    )
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "spin"
    )

    Surface(
        color = themeBg(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = themeSurface(),
                border = BorderStroke(1.dp, themeBorder()),
                modifier = Modifier
                    .size(40.dp)
                    .bounceClick(onClick = onBack)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = themeTextPrimary(),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Hall of Fame",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = themeTextPrimary(),
                    letterSpacing = (-0.3).sp,
                    fontFamily = InterFont
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .alpha(dotAlpha)
                            .clip(CircleShape)
                            .background(SuccessGreen)
                    )
                    Text(
                        text = "LIVE REAL USER RANKINGS",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.1.sp,
                        color = themeTextTertiary(),
                        fontFamily = InterFont
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = themeSurface(),
                border = BorderStroke(1.dp, themeBorder()),
                modifier = Modifier
                    .size(38.dp)
                    .bounceClick(onClick = onRefresh)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Refresh",
                        tint = if (isRefreshing) GoldRankColor else themeTextSecondary(),
                        modifier = Modifier
                            .size(18.dp)
                            .then(if (isRefreshing) Modifier.rotate(spinAngle) else Modifier)
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = themeSurface(),
                border = BorderStroke(1.dp, themeBorder()),
                modifier = Modifier
                    .size(38.dp)
                    .bounceClick(onClick = onOpenRules)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = "Ranking Rules",
                        tint = themeTextSecondary(),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RuleTierRow(title: String, desc: String, accent: Color) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = themeBg(),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.28f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Column {
                Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = themeTextPrimary(), fontFamily = InterFont)
                Text(desc, fontSize = 11.sp, color = themeTextSecondary(), fontFamily = InterFont)
            }
        }
    }
}

@Composable
private fun PodiumCrownVector(color: Color, size: Dp = 26.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val path = Path().apply {
            moveTo(w * 0.12f, h * 0.78f)
            lineTo(w * 0.08f, h * 0.30f)
            lineTo(w * 0.32f, h * 0.54f)
            lineTo(w * 0.50f, h * 0.16f)
            lineTo(w * 0.68f, h * 0.54f)
            lineTo(w * 0.92f, h * 0.30f)
            lineTo(w * 0.88f, h * 0.78f)
            close()
        }
        drawPath(
            path = path,
            brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.95f), color))
        )
        drawPath(
            path = path,
            color = color.copy(alpha = 0.9f),
            style = Stroke(width = 1.6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.12f, h * 0.82f),
            size = androidx.compose.ui.geometry.Size(w * 0.76f, h * 0.12f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
        )
    }
}

@Composable
private fun VectorTierBadge(badgeType: String, compact: Boolean = false) {
    val badgeLower = badgeType.lowercase().trim()
    val isElite = badgeLower.contains("elite") || badgeLower == "gold_vip"
    val isVip = !isElite && badgeLower.contains("vip")
    if (!isElite && !isVip) return

    val bgBrush = if (isElite) {
        Brush.linearGradient(listOf(EliteCyan, EliteViolet))
    } else {
        Brush.linearGradient(listOf(GoldRankColor, GoldRankDeep))
    }
    val icon = if (isElite) Icons.Rounded.Diamond else Icons.Rounded.WorkspacePremium
    val label = if (isElite) "ELITE" else "VIP"

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgBrush)
            .padding(
                horizontal = if (compact) 5.dp else 6.dp,
                vertical = if (compact) 1.5.dp else 2.dp
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF0B0A10),
                modifier = Modifier.size(if (compact) 9.dp else 10.dp)
            )
            Text(
                text = label,
                fontSize = if (compact) 8.sp else 9.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp,
                color = Color(0xFF0B0A10),
                fontFamily = InterFont
            )
        }
    }
}

@Composable
private fun VectorCoinPill(coins: Int, accentColor: Color = GoldRankColor, compact: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(50),
        color = accentColor.copy(alpha = 0.13f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.34f))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(
                horizontal = if (compact) 7.dp else 9.dp,
                vertical = if (compact) 3.dp else 4.dp
            )
        ) {
            Box(
                modifier = Modifier
                    .size(if (compact) 12.dp else 14.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(0xFFFFE5B4), accentColor))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Star,
                    contentDescription = null,
                    tint = Color(0xFF1A1205),
                    modifier = Modifier.size(if (compact) 8.dp else 9.dp)
                )
            }
            Text(
                text = formatCoinCount(coins),
                fontSize = if (compact) 11.sp else 12.sp,
                fontWeight = FontWeight.ExtraBold,
                color = accentColor,
                maxLines = 1,
                fontFamily = InterFont
            )
        }
    }
}

@Composable
private fun PodiumStageColumn(
    entry: LeaderboardEntry,
    accentColor: Color,
    accentDeep: Color,
    avatarSize: Dp,
    pedestalHeight: Dp,
    isWinner: Boolean,
    isCurrentUser: Boolean,
    modifier: Modifier = Modifier
) {
    val crownFloat = rememberInfiniteTransition(label = "crown_float")
    val crownOffset by crownFloat.animateFloat(
        initialValue = -2f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1400, easing = EaseInOut), RepeatMode.Reverse),
        label = "c_off"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        if (isWinner) {
            Box(modifier = Modifier.offset(y = crownOffset.dp)) {
                PodiumCrownVector(color = accentColor, size = 28.dp)
            }
            Spacer(Modifier.height(2.dp))
        } else {
            Surface(
                shape = RoundedCornerShape(50),
                color = accentColor.copy(alpha = 0.16f),
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f))
            ) {
                Text(
                    text = if (entry.rank == 2) "2ND" else "3RD",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.8.sp,
                    color = accentColor,
                    fontFamily = InterFont,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
            Spacer(Modifier.height(4.dp))
        }

        AnimatedLeaderboardAvatar(
            avatarUrl = entry.avatarUrl,
            name = entry.name,
            badgeType = entry.badgeType,
            avatarSize = avatarSize,
            rank = entry.rank,
            rankColor = accentColor
        )

        Spacer(Modifier.height(10.dp))

        // 3D Glassmorphic Tiered Pedestal
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(pedestalHeight)
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 14.dp, bottomEnd = 14.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            accentColor.copy(alpha = if (isWinner) 0.22f else 0.14f),
                            accentDeep.copy(alpha = 0.06f),
                            themeBg().copy(alpha = 0.55f)
                        )
                    )
                )
                .border(
                    width = if (isWinner) 1.2.dp else 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            accentColor.copy(alpha = if (isWinner) 0.72f else 0.42f),
                            accentColor.copy(alpha = 0.08f)
                        )
                    ),
                    shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 14.dp, bottomEnd = 14.dp)
                )
                .padding(horizontal = 6.dp, vertical = 10.dp)
        ) {
            // Subtle large background numeral watermark
            Text(
                text = "${entry.rank}",
                fontSize = if (isWinner) 46.sp else 38.sp,
                fontWeight = FontWeight.Black,
                color = accentColor.copy(alpha = 0.08f),
                modifier = Modifier.align(Alignment.BottomEnd),
                fontFamily = InterFont
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxSize()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (isCurrentUser) "${entry.name} (YOU)" else entry.name,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = if (isWinner) 12.sp else 11.sp,
                        color = if (isCurrentUser) GoldRankColor else themeTextPrimary(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        fontFamily = InterFont
                    )
                    Spacer(Modifier.height(4.dp))
                    VectorTierBadge(badgeType = entry.badgeType, compact = true)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    VectorCoinPill(coins = entry.coins, accentColor = accentColor, compact = true)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${entry.watchedCount} watched",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = themeTextTertiary(),
                        maxLines = 1,
                        fontFamily = InterFont
                    )
                }
            }
        }
    }
}

@Composable
private fun YourStandingCard(
    selfEntry: LeaderboardEntry?,
    fallbackProfile: UserProfile?,
    fallbackCoins: Int,
    fallbackWatched: Int,
    onBoostClick: () -> Unit
) {
    val activeBadge = selfEntry?.badgeType ?: fallbackProfile?.vipBadge?.takeIf {
        val exp = AdManager.parseDateRobust(fallbackProfile.vipBadgeExpires)
        exp == null || exp.isAfter(java.time.Instant.now())
    }.orEmpty()
    val displayName = selfEntry?.name ?: fallbackProfile?.displayName ?: "You"
    val coins = maxOf(selfEntry?.coins ?: 0, fallbackCoins)
    val watched = maxOf(selfEntry?.watchedCount ?: 0, fallbackWatched)
    val rankLabel = selfEntry?.let { "#${it.rank}" } ?: "Unranked"

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        NexiRed.copy(alpha = 0.22f),
                        GoldRankColor.copy(alpha = 0.14f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    listOf(
                        NexiRed.copy(alpha = 0.65f),
                        GoldRankColor.copy(alpha = 0.55f)
                    )
                ),
                shape = RoundedCornerShape(20.dp)
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(listOf(NexiRedLight, NexiRedDark))),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (selfEntry != null) rankLabel else "—",
                    fontSize = if (selfEntry != null && selfEntry.rank < 10) 16.sp else 14.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    fontFamily = InterFont
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "YOUR STANDING",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                        color = GoldRankColor,
                        fontFamily = InterFont
                    )
                    VectorTierBadge(badgeType = activeBadge, compact = true)
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = displayName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = themeTextPrimary(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontFamily = InterFont
                )
                Text(
                    text = "${formatCoinCount(coins)} coins • $watched watched",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = themeTextSecondary(),
                    fontFamily = InterFont
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(listOf(GoldRankColor, GoldRankDeep)))
                    .bounceClick(onClick = onBoostClick)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.TrendingUp,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Boost",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.Black,
                        fontFamily = InterFont
                    )
                }
            }
        }
    }
}

/** Animated Avatar Ring (shared elite/vip celestial halo aesthetic) */
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
    val totalContainerSize = avatarSize + 26.dp

    Box(
        modifier = modifier.size(totalContainerSize),
        contentAlignment = Alignment.Center
    ) {
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
                initialValue = if (isElite) 0.45f else 0.35f,
                targetValue = if (isElite) 0.80f else 0.70f,
                animationSpec = infiniteRepeatable(
                    tween(if (isElite) 1000 else 1250, easing = EaseInOut),
                    RepeatMode.Reverse
                ),
                label = "lb_glowA"
            )
            val glowBrush = if (isElite) {
                Brush.radialGradient(
                    colors = listOf(
                        EliteViolet.copy(alpha = glowAlpha),
                        EliteCyan.copy(alpha = glowAlpha * 0.6f),
                        Color.Transparent
                    )
                )
            } else {
                Brush.radialGradient(
                    colors = listOf(
                        GoldRankColor.copy(alpha = glowAlpha),
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

        val ringAngle by infiniteTransition.animateFloat(
            initialValue = 0f, targetValue = 360f,
            animationSpec = infiniteRepeatable(
                tween(if (isElite) 3000 else if (isVip) 4500 else 6500, easing = LinearEasing)
            ),
            label = "lb_ringA"
        )
        val ringColors = if (isElite) {
            listOf(EliteCyan, EliteViolet, ElitePink, Color.White, EliteCyan)
        } else if (isVip) {
            listOf(GoldRankColor, Color(0xFFFFE8D4), GoldRankDeep, Color(0xFFFFE9A8), GoldRankColor)
        } else {
            listOf(rankColor, rankColor.copy(alpha = 0.35f), Color.White.copy(alpha = 0.5f), rankColor)
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

        if (isElite || isVip) {
            val satAngle by infiniteTransition.animateFloat(
                initialValue = 0f, targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    tween(if (isElite) 3500 else 5000, easing = LinearEasing)
                ),
                label = "lb_satA"
            )
            val satColors = if (isElite) {
                listOf(EliteCyan, EliteViolet, ElitePink)
            } else {
                listOf(GoldRankColor, GoldRankColor, GoldRankColor)
            }
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
                            .background(EliteCyan, CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = (-6).dp, y = (-6).dp)
                            .size(4.dp)
                            .scale(1.6f - sparkScale)
                            .alpha(1.2f - sparkAlpha)
                            .background(ElitePink, CircleShape)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-4).dp)
            ) {
                val textStr = if (isElite) "PRO" else "VIP"
                val textColor = if (isElite) null else Color(0xFFFFD6AA)
                val textBrush = if (isElite) Brush.linearGradient(listOf(EliteCyan, ElitePink)) else null

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

        Box(
            modifier = Modifier
                .size(avatarSize)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0xFF9333EA), Color(0xFF4F46E5)))),
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
                    fontSize = (avatarSize.value * 0.42f).sp
                )
            }
        }

        // Crisp Metallic Rank Pill
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = 5.dp)
                .clip(RoundedCornerShape(50))
                .background(Brush.linearGradient(listOf(Color.White, rankColor)))
                .border(1.5.dp, themeBg(), RoundedCornerShape(50))
                .padding(horizontal = 8.dp, vertical = 1.5.dp)
        ) {
            Text(
                text = "#$rank",
                color = Color(0xFF0D0B12),
                fontWeight = FontWeight.Black,
                fontSize = 10.sp,
                fontFamily = InterFont
            )
        }
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

    val rowBgBrush = when {
        isCurrentUser -> Brush.horizontalGradient(
            listOf(GoldRankColor.copy(alpha = 0.16f), NexiRed.copy(alpha = 0.08f))
        )
        isElite -> Brush.horizontalGradient(
            listOf(EliteViolet.copy(alpha = 0.12f), themeCard())
        )
        isVip -> Brush.horizontalGradient(
            listOf(GoldRankColor.copy(alpha = 0.08f), themeCard())
        )
        else -> Brush.horizontalGradient(listOf(themeCard(), themeSurface()))
    }

    val borderColor = when {
        isCurrentUser -> GoldRankColor.copy(alpha = 0.65f)
        isElite -> EliteViolet.copy(alpha = 0.35f)
        isVip -> GoldRankColor.copy(alpha = 0.28f)
        else -> themeBorder()
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(rowBgBrush)
            .border(
                width = if (isCurrentUser) 1.2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(18.dp)
            )
            .padding(horizontal = 12.dp, vertical = 11.dp)
    ) {
        // Rank Medallion Box
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (isCurrentUser) GoldRankColor.copy(alpha = 0.22f)
                    else themeBg().copy(alpha = 0.65f)
                )
                .border(
                    width = 1.dp,
                    color = if (isCurrentUser) GoldRankColor.copy(alpha = 0.5f) else themeBorder(),
                    shape = RoundedCornerShape(10.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${entry.rank}",
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (isCurrentUser) GoldRankColor else themeTextPrimary(),
                fontFamily = InterFont
            )
        }

        Spacer(Modifier.width(10.dp))

        // Tier-ringed Avatar
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(
                    when {
                        isElite -> Brush.linearGradient(listOf(EliteCyan, EliteViolet, ElitePink))
                        isVip -> Brush.linearGradient(listOf(GoldRankColor, GoldRankDeep))
                        else -> Brush.linearGradient(listOf(themeBorderStrong(), themeBorder()))
                    }
                )
                .padding(2.dp),
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
                        .background(themeBg())
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Color(0xFF2B2542), Color(0xFF171422)))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = entry.name.take(1).uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        fontFamily = InterFont
                    )
                }
            }
        }

        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = entry.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isCurrentUser) GoldRankColor else themeTextPrimary(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                    fontFamily = InterFont
                )

                if (isCurrentUser) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(NexiRed.copy(alpha = 0.25f))
                            .border(0.8.dp, NexiRed.copy(alpha = 0.6f), RoundedCornerShape(5.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "YOU",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Black,
                            color = NexiRedLight,
                            fontFamily = InterFont
                        )
                    }
                }

                VectorTierBadge(badgeType = entry.badgeType, compact = true)
            }

            Spacer(Modifier.height(2.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayCircle,
                    contentDescription = null,
                    tint = themeTextTertiary(),
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = "${entry.watchedCount} watched",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = themeTextTertiary(),
                    fontFamily = InterFont
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        VectorCoinPill(coins = entry.coins, accentColor = GoldRankColor, compact = false)
    }
}

@Composable
private fun LeaderboardLoadingSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(themeSurface())
                .border(1.dp, themeBorder(), RoundedCornerShape(26.dp))
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    ShimmerBox(modifier = Modifier.size(56.dp).clip(CircleShape), cornerRadius = 28)
                    Spacer(Modifier.height(10.dp))
                    ShimmerBox(modifier = Modifier.fillMaxWidth().height(106.dp), cornerRadius = 16)
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1.15f)
                ) {
                    ShimmerBox(modifier = Modifier.size(72.dp).clip(CircleShape), cornerRadius = 36)
                    Spacer(Modifier.height(10.dp))
                    ShimmerBox(modifier = Modifier.fillMaxWidth().height(128.dp), cornerRadius = 16)
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    ShimmerBox(modifier = Modifier.size(54.dp).clip(CircleShape), cornerRadius = 27)
                    Spacer(Modifier.height(10.dp))
                    ShimmerBox(modifier = Modifier.fillMaxWidth().height(98.dp), cornerRadius = 16)
                }
            }
        }

        ShimmerBox(modifier = Modifier.fillMaxWidth().height(72.dp), cornerRadius = 20)
        repeat(6) {
            ShimmerBox(modifier = Modifier.fillMaxWidth().height(66.dp), cornerRadius = 18)
        }
    }
}

@Composable
private fun OfflineLeaderboardState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(themeSurface())
            .border(1.dp, themeBorder(), RoundedCornerShape(24.dp))
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(NexiRed.copy(alpha = 0.14f))
                    .border(1.dp, NexiRed.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.EmojiEvents,
                    contentDescription = null,
                    tint = NexiRedLight,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Leaderboard Currently Offline",
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = themeTextPrimary(),
                fontFamily = InterFont
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "The Hall of Fame is undergoing maintenance or temporarily paused by admin.",
                fontSize = 13.sp,
                color = themeTextSecondary(),
                textAlign = TextAlign.Center,
                fontFamily = InterFont
            )
        }
    }
}

@Composable
private fun EmptyLeaderboardState(onEarnClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(themeSurface())
            .border(1.dp, themeBorder(), RoundedCornerShape(24.dp))
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(GoldRankColor.copy(alpha = 0.14f))
                    .border(1.dp, GoldRankColor.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.EmojiEvents,
                    contentDescription = null,
                    tint = GoldRankColor,
                    modifier = Modifier.size(34.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Claim the #1 Crown",
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = themeTextPrimary(),
                fontFamily = InterFont
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Earn NexiCoins, unlock VIP or Elite Pro, and watch content to top the Hall of Fame.",
                fontSize = 13.sp,
                color = themeTextSecondary(),
                textAlign = TextAlign.Center,
                fontFamily = InterFont
            )
            Spacer(Modifier.height(18.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(listOf(GoldRankColor, GoldRankDeep)))
                    .bounceClick(onClick = onEarnClick)
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "Open Coin Center",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.Black,
                    fontFamily = InterFont
                )
            }
        }
    }
}

/**
 * Real-user fallback query if leaderboard_entries is empty.
 * Strictly returns real database users only (zero synthetic users).
 */
private suspend fun fetchRealUsersFallback(
    realProfile: UserProfile?,
    currentUserId: String?,
    realCoins: Int,
    realWatched: Int
): List<LeaderboardEntry> {
    return try {
        val profiles = SupabaseClient.main.from("profiles")
            .select(Columns.list("id", "email", "display_name", "avatar_url", "vip_badge", "vip_badge_expires"))
            .decodeList<UserProfile>()
        val balances = SupabaseClient.main.from("coin_balances")
            .select()
            .decodeList<CoinBalance>()
            .associate { it.userId to it.balance }

        val now = java.time.Instant.now()
        val scored = profiles.map { p ->
            val exp = AdManager.parseDateRobust(p.vipBadgeExpires)
            val isBadgeActive = exp == null || exp.isAfter(now)
            val badge = if (isBadgeActive) (p.vipBadge?.lowercase() ?: "none") else "none"
            val badgeWeight = when {
                badge.contains("elite") || badge == "gold_vip" -> 100_000_000L
                badge.contains("vip") -> 50_000_000L
                else -> 0L
            }
            val coins = if (p.id == currentUserId) maxOf(realCoins, balances[p.id] ?: 0) else (balances[p.id] ?: 0)
            val watched = if (p.id == currentUserId) realWatched else 0
            val score = badgeWeight + (coins.toLong() * 1000L) + watched.coerceAtMost(999)
            val displayName = p.displayName?.takeIf { it.isNotBlank() }
                ?: p.email?.substringBefore("@")
                ?: "Nexi User"

            Triple(
                score,
                coins,
                LeaderboardEntry(
                    id = "real_${p.id}",
                    rank = 1,
                    userId = p.id,
                    name = displayName,
                    avatarUrl = p.avatarUrl,
                    badgeType = badge,
                    coins = coins,
                    watchedCount = watched,
                    isFake = false
                )
            )
        }
            .sortedWith(
                compareByDescending<Triple<Long, Int, LeaderboardEntry>> { it.first }
                    .thenByDescending { it.second }
            )
            .take(15)
            .mapIndexed { index, triple ->
                triple.third.copy(rank = index + 1)
            }

        if (scored.isNotEmpty()) scored
        else if (currentUserId != null && realProfile != null) {
            listOf(
                LeaderboardEntry(
                    id = "real_$currentUserId",
                    rank = 1,
                    userId = currentUserId,
                    name = realProfile.displayName ?: "Nexi User",
                    avatarUrl = realProfile.avatarUrl,
                    badgeType = realProfile.vipBadge ?: "none",
                    coins = realCoins,
                    watchedCount = realWatched,
                    isFake = false
                )
            )
        } else emptyList()
    } catch (_: Exception) {
        if (currentUserId != null && realProfile != null) {
            listOf(
                LeaderboardEntry(
                    id = "real_$currentUserId",
                    rank = 1,
                    userId = currentUserId,
                    name = realProfile.displayName ?: "Nexi User",
                    avatarUrl = realProfile.avatarUrl,
                    badgeType = realProfile.vipBadge ?: "none",
                    coins = realCoins,
                    watchedCount = realWatched,
                    isFake = false
                )
            )
        } else emptyList()
    }
}
