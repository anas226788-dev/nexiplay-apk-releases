package com.nexiplay.app.ui.screens.coins

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.nexiplay.app.R
import com.nexiplay.app.data.util.AdManager
import com.nexiplay.app.data.util.CoinRewardHelper
import com.nexiplay.app.ui.components.bounceClick
import com.nexiplay.app.ui.navigation.Screen
import com.nexiplay.app.ui.theme.*
import com.nexiplay.app.util.findActivity
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

// -- Font Setup --
val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)
val JakartaFont = FontFamily(
    Font(googleFont = GoogleFont("Plus Jakarta Sans"), fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = GoogleFont("Plus Jakarta Sans"), fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = GoogleFont("Plus Jakarta Sans"), fontProvider = provider, weight = FontWeight.SemiBold),
    Font(googleFont = GoogleFont("Plus Jakarta Sans"), fontProvider = provider, weight = FontWeight.Bold),
    Font(googleFont = GoogleFont("Plus Jakarta Sans"), fontProvider = provider, weight = FontWeight.ExtraBold)
)
val OutfitFont = FontFamily(
    Font(googleFont = GoogleFont("Outfit"), fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = GoogleFont("Outfit"), fontProvider = provider, weight = FontWeight.Bold),
    Font(googleFont = GoogleFont("Outfit"), fontProvider = provider, weight = FontWeight.Black)
)
val JetBrainsFont = FontFamily(
    Font(googleFont = GoogleFont("JetBrains Mono"), fontProvider = provider, weight = FontWeight.Bold)
)

@Composable
fun CoinCenterScreen(navController: NavController, vm: CoinViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val badgeTick by AdManager.badgeUpdateTick.collectAsState()
    val context = LocalContext.current
    val activity = context.findActivity()
    var isAdLoading by remember { mutableStateOf(false) }

    if (state.showPurchaseDialog) {
        val accentColor = if (state.isErrorDialog) ErrorRed else CoinGoldPrimary
        androidx.compose.ui.window.Dialog(onDismissRequest = { vm.dismissPurchaseDialog() }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(themeSurface(), RoundedCornerShape(16.dp))
                    .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (state.isErrorDialog) Icons.Default.Info else Icons.Default.Verified,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        state.dialogTitle,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = themeTextPrimary(),
                        fontFamily = JakartaFont,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        state.purchaseSuccessMessage ?: "",
                        fontSize = 14.sp,
                        color = themeTextSecondary(),
                        fontFamily = JakartaFont,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = { vm.dismissPurchaseDialog() },
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text(
                            if (state.isErrorDialog) "GOT IT" else "AWESOME",
                            color = if (state.isErrorDialog) Color.White else CoinBg,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            fontFamily = JakartaFont
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit, badgeTick) {
        vm.loadData(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CoinBg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .padding(bottom = 100.dp) // Room for bottom nav
    ) {
        if (!state.isLoggedIn && !state.isLoading) {
            LoginRequiredBox(navController)
        } else {
            HeroBalanceCard(state, context, vm)
            Spacer(Modifier.height(24.dp))
            StreakCalendarCard(state, vm, context)
            Spacer(Modifier.height(24.dp))
            InviteFriendsSection(state, vm, context)
            Spacer(Modifier.height(24.dp))
            EarnCoinsSection(
                state = state,
                activity = activity,
                context = context,
                vm = vm,
                navController = navController,
                isAdLoading = isAdLoading,
                onAdLoadChange = { isAdLoading = it }
            )
            Spacer(Modifier.height(24.dp))
            CoinShopSection(state, vm, context, navController)
        }
    }
}

@Composable
private fun HeroBalanceCard(state: CoinState, context: Context, vm: CoinViewModel) {
    val bal = state.balance
    val targetBalance = bal?.balance ?: 0
    val targetEarned = bal?.totalEarned ?: 0
    val targetSpent = bal?.totalSpent ?: 0

    var balanceValue by remember { mutableIntStateOf(targetBalance) }
    var earnedValue by remember { mutableIntStateOf(targetEarned) }
    var spentValue by remember { mutableIntStateOf(targetSpent) }

    LaunchedEffect(targetBalance) {
        val anim = Animatable(balanceValue.toFloat())
        anim.animateTo(
            targetValue = targetBalance.toFloat(),
            animationSpec = tween(durationMillis = 900, easing = EaseOut)
        ) { balanceValue = value.toInt() }
    }
    LaunchedEffect(targetEarned) {
        val anim = Animatable(earnedValue.toFloat())
        anim.animateTo(
            targetValue = targetEarned.toFloat(),
            animationSpec = tween(durationMillis = 900, easing = EaseOut)
        ) { earnedValue = value.toInt() }
    }
    LaunchedEffect(targetSpent) {
        val anim = Animatable(spentValue.toFloat())
        anim.animateTo(
            targetValue = targetSpent.toFloat(),
            animationSpec = tween(durationMillis = 900, easing = EaseOut)
        ) { spentValue = value.toInt() }
    }

    val isElite = AdManager.isUserElite()
    val isVip = AdManager.isUserPremium()
    val isBoosted = vm.isBoostActive(context)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF2E2E42).copy(alpha = 0.8f),
                        Color(0xFF13131A).copy(alpha = 0.95f)
                    )
                )
            )
            .border(
                1.5.dp,
                Brush.linearGradient(listOf(CoinGoldPrimary.copy(alpha = 0.6f), Color.Transparent, Color.White.copy(alpha = 0.1f))),
                RoundedCornerShape(28.dp)
            )
            .padding(horizontal = 24.dp, vertical = 28.dp)
    ) {
        // Subtle glow effect behind the text
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = 20.dp)
                .size(120.dp)
                .background(
                    Brush.radialGradient(listOf(CoinGoldPrimary.copy(alpha = 0.15f), Color.Transparent)),
                    CircleShape
                )
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    "TOTAL BALANCE",
                    fontFamily = JakartaFont,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.4f),
                    letterSpacing = 3.sp
                )
                if (isElite || isVip || isBoosted) {
                    Spacer(Modifier.width(8.dp))
                    val pillText = when {
                        isElite -> "💎 ELITE PRO · 2X"
                        isBoosted -> "⚡ 2X BOOST"
                        else -> "👑 VIP"
                    }
                    val pillColor = when {
                        isElite -> Color(0xFFB09AFF)
                        isBoosted -> Color(0xFF06B6D4)
                        else -> CoinGoldPrimary
                    }
                    Box(
                        modifier = Modifier
                            .background(pillColor.copy(alpha = 0.18f), RoundedCornerShape(999.dp))
                            .border(1.dp, pillColor.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            pillText,
                            fontFamily = JakartaFont,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 9.sp,
                            color = pillColor
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                // Glowy Coin Circle
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(Brush.linearGradient(listOf(CoinGoldLight, CoinGoldPrimary, CoinGoldDark)), CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🪙", fontSize = 22.sp)
                }
                Spacer(Modifier.width(16.dp))

                // Huge Balance Number
                Text(
                    text = balanceValue.toString(),
                    fontFamily = OutfitFont,
                    fontWeight = FontWeight.Black,
                    fontSize = 72.sp,
                    style = TextStyle(
                        brush = Brush.linearGradient(
                            colors = listOf(Color.White, CoinGoldLight, CoinGoldPrimary)
                        ),
                        shadow = Shadow(
                            color = CoinGoldPrimary.copy(alpha = 0.3f),
                            blurRadius = 15f
                        )
                    )
                )
            }

            Spacer(Modifier.height(28.dp))

            // Earned vs Spent Cards
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                // Earned Box
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color(0xFF0F1A15).copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                        .border(1.dp, Color(0xFF14C38E).copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.TrendingUp, contentDescription = "Earned", tint = Color(0xFF14C38E), modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "EARNED",
                                fontFamily = JakartaFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = Color(0xFF14C38E).copy(alpha = 0.8f),
                                letterSpacing = 1.5.sp
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "+%,d".format(earnedValue),
                            fontFamily = OutfitFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = Color.White
                        )
                    }
                }

                // Spent Box
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color(0xFF2A1015).copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                        .border(1.dp, Color(0xFFFF4C4C).copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.TrendingDown, contentDescription = "Spent", tint = Color(0xFFFF4C4C), modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "SPENT",
                                fontFamily = JakartaFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = Color(0xFFFF4C4C).copy(alpha = 0.8f),
                                letterSpacing = 1.5.sp
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "-%,d".format(spentValue),
                            fontFamily = OutfitFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StreakCalendarCard(state: CoinState, vm: CoinViewModel, context: Context) {
    val infiniteTransition = rememberInfiniteTransition(label = "flame")
    val flameScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flameScale"
    )

    val streakCount = state.streak?.currentStreak ?: 0
    val longestStreak = state.streak?.longestStreak ?: 0
    val claimedToday = state.claimStatus == ClaimStatus.ALREADY_CLAIMED || state.claimStatus == ClaimStatus.CLAIMED_NOW
    val isClaiming = state.claimStatus == ClaimStatus.CLAIMING

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(colors = listOf(CoinCard, Color(0xFF1E1E2D), Color(0xFF252535))))
            .border(1.dp, CoinBorder.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
            .padding(24.dp)
    ) {
        Column {
            // Header Row
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("🔥", fontSize = 24.sp, modifier = Modifier.scale(flameScale))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "$streakCount Day Streak",
                        fontFamily = JakartaFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                    Text(
                        "Best: $longestStreak days",
                        fontFamily = JakartaFont,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.35f)
                    )
                }

                // Claim Button / Status Badge
                when {
                    isClaiming -> {
                        Box(
                            modifier = Modifier
                                .background(CoinGoldPrimary.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("CLAIMING...", fontFamily = JakartaFont, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = CoinGoldPrimary)
                        }
                    }
                    claimedToday -> {
                        Box(
                            modifier = Modifier
                                .background(SuccessGreen.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                                .border(1.dp, SuccessGreen, RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Check, null, tint = SuccessGreen, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Claimed", fontFamily = JakartaFont, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SuccessGreen)
                            }
                        }
                    }
                    else -> {
                        val nextReward = vm.getNextClaimReward(context)
                        Button(
                            onClick = { vm.claimDaily(context) },
                            colors = ButtonDefaults.buttonColors(containerColor = CoinGoldPrimary),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("CLAIM +$nextReward", fontFamily = JakartaFont, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = CoinBg)
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Calendar Grid (Actual Dates — only mark days that were genuinely claimed in the active streak!)
            val days = listOf("S", "M", "T", "W", "T", "F", "S")
            val today = LocalDate.now()
            val currentDayOfWeek = today.dayOfWeek.value // 1 (Mon) to 7 (Sun)
            val sunday = today.minusDays((currentDayOfWeek % 7).toLong())
            val weekDates = (0..6).map { sunday.plusDays(it.toLong()) }

            val lastClaimDate = vm.parseLastClaimLocalDate(state.streak)
                ?: if (claimedToday) today else null

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                days.forEachIndexed { index, day ->
                    val date = weekDates[index]
                    val isToday = date == today

                    // A date is completed ONLY if we have an active streak (> 0) and `date` falls within
                    // [lastClaimDate - (streakCount - 1) .. lastClaimDate]
                    val isCompleted = if (streakCount > 0 && lastClaimDate != null && !date.isAfter(lastClaimDate)) {
                        val diff = ChronoUnit.DAYS.between(date, lastClaimDate)
                        diff in 0 until streakCount.toLong()
                    } else {
                        false
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = if (isToday && !claimedToday && !isClaiming) {
                            Modifier.bounceClick { vm.claimDaily(context) }
                        } else Modifier
                    ) {
                        Text(day, fontFamily = JakartaFont, fontSize = 9.sp, color = Color.White.copy(alpha = 0.25f))
                        Spacer(Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isCompleted -> CoinGoldPrimary.copy(alpha = 0.22f)
                                        isToday -> CoinGoldPrimary.copy(alpha = 0.08f)
                                        else -> Color.Transparent
                                    }
                                )
                                .border(
                                    1.dp,
                                    when {
                                        isCompleted -> CoinGoldPrimary.copy(alpha = 0.5f)
                                        isToday -> CoinGoldPrimary
                                        else -> Color.White.copy(alpha = 0.1f)
                                    },
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isCompleted) {
                                Icon(Icons.Default.Check, null, tint = CoinGoldPrimary, modifier = Modifier.size(16.dp))
                            } else {
                                Text(
                                    date.dayOfMonth.toString(),
                                    fontFamily = OutfitFont,
                                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 12.sp,
                                    color = if (isToday) Color.White else Color.White.copy(alpha = 0.3f)
                                )
                            }

                            if (isToday && !isCompleted) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 2.dp, y = (-2).dp)
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(CoinGoldPrimary)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                when {
                    claimedToday -> "✓ Daily reward claimed! Come back tomorrow for Day ${streakCount + 1}."
                    streakCount > 0 -> "Tap CLAIM above to extend your $streakCount-day streak!"
                    else -> "Tap CLAIM above to start your Day 1 streak today!"
                },
                fontFamily = JakartaFont,
                fontSize = 11.sp,
                color = if (claimedToday) SuccessGreen.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.4f),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun InviteFriendsSection(state: CoinState, vm: CoinViewModel, context: Context) {
    val profile = state.userProfile
    var referralCodeInput by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var isRedeeming by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerTranslateAnim by infiniteTransition.animateFloat(
        initialValue = -500f,
        targetValue = 1500f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )
    val shimmerBrush = Brush.linearGradient(
        colors = listOf(Color.Transparent, CoinGoldPrimary.copy(alpha = 0.15f), Color.Transparent),
        start = Offset(shimmerTranslateAnim - 200f, shimmerTranslateAnim - 200f),
        end = Offset(shimmerTranslateAnim, shimmerTranslateAnim)
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(CoinCard, Color(0xFF1E1E2D), Color(0xFF252535))))
            .border(1.dp, CoinBorder.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
            .padding(24.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(48.dp).background(Color(0xFF3B82F6).copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) { Text("🤝", fontSize = 24.sp) }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Invite Friends", fontFamily = JakartaFont, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                    Text("Get 50 coins for every friend who joins!", fontFamily = JakartaFont, fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
                }
            }

            Spacer(Modifier.height(20.dp))

            // Referral Code Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(16.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
            ) {
                Box(modifier = Modifier.matchParentSize().background(shimmerBrush))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Your Code", fontFamily = JakartaFont, fontSize = 10.sp, color = Color.White.copy(alpha = 0.4f))
                        Spacer(Modifier.height(4.dp))
                        Text(
                            profile?.referralCode ?: "LOADING...",
                            fontFamily = JetBrainsFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White,
                            letterSpacing = 2.sp
                        )
                    }
                    Button(
                        onClick = {
                            val code = profile?.referralCode
                            if (code.isNullOrBlank()) {
                                Toast.makeText(context, "Referral code is still loading...", Toast.LENGTH_SHORT).show()
                            } else {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("Referral Code", code)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Referral code $code copied!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CoinGoldPrimary),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text("COPY", color = CoinBg, fontFamily = JakartaFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            // Redeem Section
            if (profile != null && profile.referredBy == null) {
                Spacer(Modifier.height(20.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(
                        value = referralCodeInput,
                        onValueChange = { referralCodeInput = it.uppercase() },
                        textStyle = TextStyle(fontFamily = JetBrainsFont, color = Color.White, fontSize = 14.sp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .background(CoinSurface, RoundedCornerShape(12.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        decorationBox = { innerTextField ->
                            if (referralCodeInput.isEmpty()) Text("Enter friend's code", color = Color.White.copy(alpha = 0.3f), fontSize = 14.sp, fontFamily = JakartaFont)
                            innerTextField()
                        }
                    )
                    Spacer(Modifier.width(12.dp))
                    Button(
                        onClick = {
                            if (referralCodeInput.isBlank() || isRedeeming) return@Button
                            isRedeeming = true
                            scope.launch {
                                val result = vm.redeemReferralCode(referralCodeInput.trim())
                                if (result.isSuccess) {
                                    Toast.makeText(context, result.getOrNull(), Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(
                                        context,
                                        result.exceptionOrNull()?.let { com.nexiplay.app.ui.util.UserFacingError.from(it, "Couldn't redeem that code. Please try again.") }
                                            ?: "Couldn't redeem that code. Please try again.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                                isRedeeming = false
                                referralCodeInput = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text(if (isRedeeming) "..." else "REDEEM", fontFamily = JakartaFont, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "You get 5 coins. Your friend gets 50 coins.",
                    fontFamily = JakartaFont,
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.3f)
                )
            }
        }
    }
}

@Composable
private fun EarnCoinsSection(
    state: CoinState,
    activity: android.app.Activity?,
    context: Context,
    vm: CoinViewModel,
    navController: NavController,
    isAdLoading: Boolean,
    onAdLoadChange: (Boolean) -> Unit
) {
    val isBoosted = vm.isBoostActive(context)
    val mult = if (isBoosted) 2 else 1

    val claimedToday = state.claimStatus == ClaimStatus.ALREADY_CLAIMED || state.claimStatus == ClaimStatus.CLAIMED_NOW
    val nextDailyReward = vm.getNextClaimReward(context)

    val watchCount = CoinRewardHelper.getDailyWatchCount(context)
    val watchRewarded = CoinRewardHelper.isDailyWatchRewarded(context)

    val dlCount = CoinRewardHelper.getDailyDownloadCount(context)
    val dlRewarded = CoinRewardHelper.isDailyDownloadRewarded(context)

    val currentStreak = state.streak?.currentStreak ?: 0
    val longestStreak = state.streak?.longestStreak ?: 0
    val is7Eligible = currentStreak >= 7 || longestStreak >= 7
    val is30Eligible = currentStreak >= 30 || longestStreak >= 30
    val is7Claimed = vm.isStreakMilestoneClaimed(7, context)
    val is30Claimed = vm.isStreakMilestoneClaimed(30, context)

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Earn Coins", fontFamily = JakartaFont, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.background(CoinGoldPrimary.copy(alpha = 0.15f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 2.dp)
            ) { Text("9 TASKS", fontFamily = JakartaFont, fontWeight = FontWeight.Bold, fontSize = 9.sp, color = CoinGoldPrimary) }
            if (isBoosted) {
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.background(Color(0xFF06B6D4).copy(alpha = 0.18f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 2.dp)
                ) { Text("⚡ 2X ACTIVE", fontFamily = JakartaFont, fontWeight = FontWeight.Bold, fontSize = 9.sp, color = Color(0xFF06B6D4)) }
            }
        }
        Spacer(Modifier.height(16.dp))

        // 1. Featured Ad Task
        val adRewardAmount = 10 * mult
        TaskItem(
            title = if (isAdLoading) "Loading Ad..." else "Watch Reward Ad",
            desc = if (isBoosted) "Watch a short ad to earn instantly (2x Boost!)" else "Watch a short ad to earn instantly",
            icon = Icons.Rounded.PlayCircle,
            reward = "+$adRewardAmount",
            isFeatured = true,
            onClick = {
                if (!isAdLoading) {
                    onAdLoadChange(true)
                    Toast.makeText(context, "Loading ad, please wait...", Toast.LENGTH_SHORT).show()
                    if (activity != null) {
                        AdManager.showRewardedAd(
                            context = context,
                            onRewarded = {
                                vm.claimAdReward(10, context)
                                onAdLoadChange(false)
                                Toast.makeText(context, "Congratulations! You received +$adRewardAmount Coins 🪙", Toast.LENGTH_LONG).show()
                            },
                            onFailed = { errorMessage ->
                                onAdLoadChange(false)
                                Toast.makeText(context, "Ad Error: $errorMessage", Toast.LENGTH_LONG).show()
                            }
                        )
                    } else {
                        onAdLoadChange(false)
                    }
                }
            }
        )

        // 1b. High-CTR Bonus Explorer Task (Direct Link with 10s Overlay Timer)
        if (AdManager.hasDirectLinkConfigured()) {
            val bonusReward = 15 * mult
            TaskItem(
                title = "Bonus Coin Explorer",
                desc = "Explore partner page for 10s to unlock bonus coins",
                icon = Icons.Rounded.Stars,
                reward = "+$bonusReward",
                isFeatured = true,
                onClick = {
                    AdManager.triggerDirectLinkCoinTask(
                        context = context,
                        onRewarded = {
                            vm.claimAdReward(15, context)
                            Toast.makeText(context, "Verified! +$bonusReward Bonus Coins added 🪙", Toast.LENGTH_LONG).show()
                        },
                        onUnavailableOrCooldown = { msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            )
        }

        // 2. Daily Login Task
        TaskItem(
            title = "Daily Login",
            desc = if (claimedToday) "✓ Claimed today! Come back tomorrow" else "Tap to claim your daily login reward",
            icon = Icons.Rounded.EventAvailable,
            reward = if (claimedToday) "✓ Claimed" else "CLAIM +$nextDailyReward",
            isFeatured = !claimedToday,
            onClick = {
                if (claimedToday) {
                    Toast.makeText(context, "✓ You've already claimed today's daily login reward!", Toast.LENGTH_SHORT).show()
                } else {
                    vm.claimDaily(context)
                }
            }
        )

        // 3. Watch 2 Episodes Task
        val watchRewardVal = 35 * mult
        TaskItem(
            title = "Watch 2 Episodes",
            desc = if (watchRewarded) {
                "✓ Daily watch bonus claimed today!"
            } else {
                "Progress today: ${minOf(watchCount, 2)}/2 episodes watched"
            },
            icon = Icons.Rounded.OndemandVideo,
            reward = if (watchRewarded) "✓ Claimed" else "+$watchRewardVal",
            onClick = {
                if (watchRewarded) {
                    Toast.makeText(context, "✓ You already earned today's +$watchRewardVal watch bonus!", Toast.LENGTH_SHORT).show()
                } else {
                    val rem = maxOf(1, 2 - watchCount)
                    Toast.makeText(context, "Watch $rem more episode(s) today to earn +$watchRewardVal Coins!", Toast.LENGTH_SHORT).show()
                    navController.navigate(Screen.Browse.route)
                }
            }
        )

        // 4. Download 2 Content Task
        val dlRewardVal = 20 * mult
        TaskItem(
            title = "Download 2 Content",
            desc = if (dlRewarded) {
                "✓ Daily download bonus claimed today!"
            } else {
                "Progress today: ${minOf(dlCount, 2)}/2 downloads completed"
            },
            icon = Icons.Rounded.CloudDownload,
            reward = if (dlRewarded) "✓ Claimed" else "+$dlRewardVal",
            onClick = {
                if (dlRewarded) {
                    Toast.makeText(context, "✓ You already earned today's +$dlRewardVal download bonus!", Toast.LENGTH_SHORT).show()
                } else {
                    val rem = maxOf(1, 2 - dlCount)
                    Toast.makeText(context, "Download $rem more episode/movie today to earn +$dlRewardVal Coins!", Toast.LENGTH_SHORT).show()
                    navController.navigate(Screen.Browse.route)
                }
            }
        )

        // 5. 7-Day Streak Milestone
        val m7Val = 50 * mult
        TaskItem(
            title = "7-Day Streak Milestone",
            desc = when {
                is7Claimed -> "✓ 7-Day streak milestone bonus claimed!"
                is7Eligible -> "🎉 Reached! Tap to claim your +$m7Val Bonus Coins!"
                else -> "Stay consistent for 7 days (${minOf(currentStreak, 7)}/7 days)"
            },
            icon = Icons.Rounded.LocalFireDepartment,
            reward = when {
                is7Claimed -> "✓ Claimed"
                is7Eligible -> "CLAIM +$m7Val"
                else -> "+$m7Val"
            },
            isFeatured = is7Eligible && !is7Claimed,
            onClick = {
                if (is7Claimed) {
                    Toast.makeText(context, "✓ You have already claimed the 7-Day streak bonus!", Toast.LENGTH_SHORT).show()
                } else if (is7Eligible) {
                    vm.claimStreakMilestone(7, 50, context)
                } else {
                    val remaining = maxOf(1, 7 - currentStreak)
                    Toast.makeText(context, "Claim daily for 7 days to unlock +$m7Val Bonus Coins! ($remaining days remaining)", Toast.LENGTH_LONG).show()
                }
            }
        )

        // 6. 30-Day Streak Milestone
        val m30Val = 250 * mult
        TaskItem(
            title = "30-Day Streak Milestone",
            desc = when {
                is30Claimed -> "✓ 30-Day streak milestone bonus claimed!"
                is30Eligible -> "🏆 Reached! Tap to claim your +$m30Val Bonus Coins!"
                else -> "A full month of dedication (${minOf(currentStreak, 30)}/30 days)"
            },
            icon = Icons.Rounded.WorkspacePremium,
            reward = when {
                is30Claimed -> "✓ Claimed"
                is30Eligible -> "CLAIM +$m30Val"
                else -> "+$m30Val"
            },
            isFeatured = is30Eligible && !is30Claimed,
            onClick = {
                if (is30Claimed) {
                    Toast.makeText(context, "✓ You have already claimed the 30-Day streak bonus!", Toast.LENGTH_SHORT).show()
                } else if (is30Eligible) {
                    vm.claimStreakMilestone(30, 250, context)
                } else {
                    val remaining = maxOf(1, 30 - currentStreak)
                    Toast.makeText(context, "Claim daily for 30 days to unlock +$m30Val Bonus Coins! ($remaining days remaining)", Toast.LENGTH_LONG).show()
                }
            }
        )

        // 7. Refer a Friend
        TaskItem(
            title = "Refer a Friend",
            desc = "Tap to share your referral code (${state.userProfile?.referralCode ?: "..."})",
            icon = Icons.Rounded.Handshake,
            reward = "+50",
            onClick = {
                val code = state.userProfile?.referralCode
                if (!code.isNullOrBlank()) {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Referral Code", code))
                    try {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "Join me on NexiPlay! Use my referral code $code in the Coins tab to get bonus coins! 🪙🔥"
                            )
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Referral Code"))
                    } catch (_: Exception) {
                        Toast.makeText(context, "Referral code $code copied to clipboard!", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "Loading your referral code...", Toast.LENGTH_SHORT).show()
                }
            }
        )

        // 8. Post Comment
        val commentVal = 1 * mult
        TaskItem(
            title = "Post Comment",
            desc = "Share your thoughts on any anime or movie",
            icon = Icons.Rounded.Forum,
            reward = "+$commentVal",
            onClick = {
                Toast.makeText(context, "Open any anime or movie and post a comment to earn +$commentVal Coin!", Toast.LENGTH_SHORT).show()
                navController.navigate(Screen.Browse.route)
            }
        )

        // 9. Report Issue
        TaskItem(
            title = "Report Issue",
            desc = "Help us improve NexiPlay",
            icon = Icons.Rounded.BugReport,
            reward = "+20"
        ) {
            navController.navigate(Screen.Contact.route)
        }
    }
}

@Composable
private fun TaskItem(
    title: String,
    desc: String,
    icon: ImageVector,
    reward: String,
    isFeatured: Boolean = false,
    onClick: () -> Unit = {}
) {
    val isClaimedBadge = reward.startsWith("✓")
    val badgeColor = if (isClaimedBadge) SuccessGreen else CoinGoldPrimary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .bounceClick(onClick = onClick)
            .background(CoinCard, RoundedCornerShape(16.dp))
            .border(
                1.dp,
                if (isFeatured) CoinGoldPrimary.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.05f),
                RoundedCornerShape(16.dp)
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(
                    if (isFeatured) Brush.linearGradient(listOf(CoinGoldPrimary, CoinGoldDark))
                    else Brush.linearGradient(listOf(Color(0xFF2A2A3A), Color(0xFF1E1E2D))),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (isFeatured) CoinBg else CoinGoldPrimary,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            if (isFeatured) {
                Text("FEATURED", fontFamily = JakartaFont, fontWeight = FontWeight.Bold, fontSize = 9.sp, color = CoinGoldPrimary, letterSpacing = 1.sp)
            }
            Text(title, fontFamily = JakartaFont, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White)
            Text(desc, fontFamily = JakartaFont, fontSize = 12.sp, color = Color.White.copy(alpha = 0.45f))
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .background(badgeColor.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                .border(1.dp, badgeColor.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(reward, fontFamily = OutfitFont, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = badgeColor)
        }
    }
}

@Composable
private fun CoinShopSection(state: CoinState, vm: CoinViewModel, context: Context, navController: NavController) {
    val now = Instant.now()

    // Check active VIP / Elite badge
    val rawBadge = (state.userProfile?.vipBadge ?: AdManager.userBadgeType).trim().lowercase()
    val badgeExpiryInstant = AdManager.parseDateRobust(state.userProfile?.vipBadgeExpires ?: AdManager.badgeExpiresAt)
    val isBadgeActive = rawBadge.isNotEmpty() && (badgeExpiryInstant == null || badgeExpiryInstant.isAfter(now))
    val isEliteActive = isBadgeActive && (rawBadge.contains("elite") || rawBadge == "gold_vip")
    val isVipActive = isBadgeActive && !isEliteActive && rawBadge.contains("vip")

    // Check active Ad-Free
    val adFreeUntil = state.adFreeExpiry ?: state.userProfile?.adFreeUntil ?: AdManager.getAdFreeExpiry(context)
    val adFreeInstant = AdManager.parseDateRobust(adFreeUntil)
    val isAdFreeActive = (adFreeInstant != null && adFreeInstant.isAfter(now)) || isBadgeActive

    // Check active 2x Coin Boost
    val boostExpiryInstant = AdManager.getDoubleBoostExpiryInstant(context)
    val isBoostActive = vm.isBoostActive(context)

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Coin Shop", fontFamily = JakartaFont, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.ShoppingCart, null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(16.dp))

        // 1. Active ELITE PRO or VIP Badge Banner
        if (isEliteActive || isVipActive) {
            val bannerColor = if (isEliteActive) Color(0xFFB09AFF) else Color(0xFFFFB878)
            val expText = if (badgeExpiryInstant != null && badgeExpiryInstant.isAfter(now)) {
                val dur = Duration.between(now, badgeExpiryInstant)
                "Expires in ${dur.toDays()}d ${dur.toHours() % 24}h"
            } else {
                "Active Membership"
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bannerColor.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
                    .border(1.dp, bannerColor.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (isEliteActive) Icons.Default.AutoAwesome else Icons.Default.WorkspacePremium,
                        null,
                        tint = bannerColor,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            if (isEliteActive) "💎 ELITE PRO Active" else "👑 VIP Badge Active",
                            fontFamily = JakartaFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = bannerColor
                        )
                        Text(
                            if (isEliteActive) "$expText · Diamond Aura · 2x Coins · 1080p · Ad-Free"
                            else "$expText · Gold Ring · Ad-Free · Ultra HD",
                            fontFamily = JakartaFont,
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.75f)
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // 2. Active Ad-Free Banner
        val effectiveAdFreeExpiry = listOfNotNull(
            adFreeInstant?.takeIf { it.isAfter(now) },
            badgeExpiryInstant?.takeIf { isBadgeActive && it.isAfter(now) }
        ).maxOrNull()
        if (isAdFreeActive && effectiveAdFreeExpiry != null) {
            val duration = Duration.between(now, effectiveAdFreeExpiry)
            val days = duration.toDays()
            val hours = duration.toHours() % 24

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SuccessGreen.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                    .border(1.dp, SuccessGreen.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Verified, null, tint = SuccessGreen, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Ad-Free Active", fontFamily = JakartaFont, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = SuccessGreen)
                        Text("Expires in ${days}d ${hours}h", fontFamily = JakartaFont, fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // 3. Active 2x Coin Boost Banner
        if (isBoostActive && boostExpiryInstant != null && boostExpiryInstant.isAfter(now)) {
            val duration = Duration.between(now, boostExpiryInstant)
            val days = duration.toDays()
            val hours = duration.toHours() % 24
            val mins = duration.toMinutes() % 60
            val timeStr = if (days > 0) "${days}d ${hours}h" else "${hours}h ${mins}m"

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF06B6D4).copy(alpha = 0.14f), RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0xFF06B6D4).copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Bolt, null, tint = Color(0xFF06B6D4), modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("⚡ 2x Coin Boost Active", fontFamily = JakartaFont, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF06B6D4))
                        Text("Expires in $timeStr · Earning double coins on all tasks", fontFamily = JakartaFont, fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        ShopItemRow(
            title = "Ad-Free 24h",
            desc = "No ads for 24 hours",
            price = 100,
            color = ErrorRed,
            enabled = !state.isPurchasing
        ) { vm.buyAdFree(1, 100, context) }

        ShopItemRow(
            title = "Ad-Free 7d",
            desc = "No ads for 7 days",
            price = 250,
            color = ErrorRed,
            enabled = !state.isPurchasing
        ) { vm.buyAdFree(7, 250, context) }

        ShopItemRow(
            title = "Priority Request",
            desc = if (isBadgeActive) "Included FREE with your VIP/Elite membership!" else "Skip the ad & put your content request first",
            price = if (isBadgeActive) 0 else 30,
            color = ErrorRed,
            badgeLabel = if (isBadgeActive) "VIP FREE" else null,
            enabled = !state.isPurchasing
        ) {
            vm.buyPriorityRequest(30, context) {
                navController.navigate(Screen.RequestContent.route)
            }
        }

        ShopItemRow(
            title = "VIP Badge",
            desc = "Gold Rotating Ring + Ad-Free & Ultra HD for 30 days",
            price = 250,
            color = Color(0xFFFFB878),
            badgeLabel = if (isVipActive) "ACTIVE · EXTEND" else null,
            enabled = !state.isPurchasing
        ) { vm.buyVipBadge("vip", 30, 250, context) }

        ShopItemRow(
            title = "ELITE PRO",
            desc = "Diamond Aura + 2x Coins, Ad-Free & 1080p for 30 days",
            price = 500,
            color = CoinGoldPrimary,
            badgeLabel = if (isEliteActive) "ACTIVE · EXTEND" else "BEST VALUE",
            enabled = !state.isPurchasing
        ) { vm.buyVipBadge("elite_pro", 30, 500, context) }

        ShopItemRow(
            title = "Double Coin Boost",
            desc = "Earn 2x coins on all tasks for 24 hours",
            price = 100,
            color = Color(0xFF06B6D4),
            badgeLabel = if (isBoostActive) "ACTIVE" else null,
            enabled = !state.isPurchasing
        ) { vm.buyDoubleCoinBoost(context) }
    }
}

@Composable
private fun ShopItemRow(
    title: String,
    desc: String,
    price: Int,
    color: Color,
    badgeLabel: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .bounceClick(onClick = { if (enabled) onClick() })
            .background(CoinCard, RoundedCornerShape(16.dp))
            .border(1.dp, if (badgeLabel != null) color.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontFamily = JakartaFont, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White)
                if (badgeLabel != null) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(color.copy(alpha = 0.18f), RoundedCornerShape(6.dp))
                            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(badgeLabel, fontFamily = JakartaFont, fontWeight = FontWeight.ExtraBold, fontSize = 8.sp, color = color)
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(desc, fontFamily = JakartaFont, fontSize = 12.sp, color = Color.White.copy(alpha = 0.45f))
        }
        Spacer(Modifier.width(12.dp))
        Button(
            onClick = onClick,
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(containerColor = color.copy(alpha = 0.15f)),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            modifier = Modifier.border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
        ) {
            Text(
                if (price == 0) "FREE" else "🪙 $price",
                fontFamily = OutfitFont,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = color
            )
        }
    }
}

@Composable
private fun LoginRequiredBox(navController: NavController) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(CoinCard, RoundedCornerShape(24.dp))
            .border(1.dp, CoinBorder, RoundedCornerShape(24.dp))
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🔒", fontSize = 48.sp)
            Spacer(Modifier.height(16.dp))
            Text("Login Required", fontFamily = JakartaFont, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White)
            Text("Login to earn and spend coins", fontFamily = JakartaFont, fontSize = 14.sp, color = Color.White.copy(alpha = 0.5f))
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { navController.navigate("login") },
                colors = ButtonDefaults.buttonColors(containerColor = CoinGoldPrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) { Text("LOGIN NOW", fontFamily = JakartaFont, fontWeight = FontWeight.Bold, color = CoinBg) }
        }
    }
}
