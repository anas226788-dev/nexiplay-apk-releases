package com.nexiplay.app.ui.screens.coins

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import com.nexiplay.app.ui.components.bounceClick
import com.nexiplay.app.ui.theme.*
import com.nexiplay.app.util.findActivity
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.Duration

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
    val context = LocalContext.current
    val activity = context.findActivity()
    var isAdLoading by remember { mutableStateOf(false) }

    if (state.showPurchaseDialog) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { vm.dismissPurchaseDialog() }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(themeSurface(), RoundedCornerShape(16.dp))
                    .border(1.dp, CoinGoldPrimary.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Verified, contentDescription = "Success", tint = CoinGoldPrimary, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Purchase Successful!",
                        fontSize = 20.sp, fontWeight = FontWeight.Bold, color = themeTextPrimary(), fontFamily = JakartaFont
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        state.purchaseSuccessMessage ?: "",
                        fontSize = 14.sp, color = themeTextSecondary(), fontFamily = JakartaFont, textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = { vm.dismissPurchaseDialog() },
                        colors = ButtonDefaults.buttonColors(containerColor = CoinGoldPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text("AWESOME", color = CoinBg, fontWeight = FontWeight.Bold, fontSize = 16.sp, fontFamily = JakartaFont)
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        vm.loadData()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CoinBg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .padding(bottom = 100.dp) // Room for bottom nav
    ) {
        if (!state.isLoggedIn) {
            LoginRequiredBox(navController)
        } else {
            HeroBalanceCard(state)
            Spacer(Modifier.height(24.dp))
            StreakCalendarCard(state, vm, context)
            Spacer(Modifier.height(24.dp))
            InviteFriendsSection(state, vm, context)
            Spacer(Modifier.height(24.dp))
            EarnCoinsSection(activity, context, vm, isAdLoading = isAdLoading, onAdLoadChange = { isAdLoading = it })
            Spacer(Modifier.height(24.dp))
            CoinShopSection(state, vm, context)
        }
    }
}

@Composable
private fun HeroBalanceCard(state: CoinState) {
    val bal = state.balance
    val targetBalance = bal?.balance ?: 0
    val targetEarned = bal?.totalEarned ?: 0
    val targetSpent = bal?.totalSpent ?: 0

    var balanceValue by remember { mutableIntStateOf(0) }
    var earnedValue by remember { mutableIntStateOf(0) }
    var spentValue by remember { mutableIntStateOf(0) }

    LaunchedEffect(targetBalance) {
        val anim = Animatable(balanceValue.toFloat())
        anim.animateTo(
            targetValue = targetBalance.toFloat(),
            animationSpec = tween(durationMillis = 1200, easing = EaseOut)
        ) { balanceValue = value.toInt() }
    }
    LaunchedEffect(targetEarned) {
        val anim = Animatable(earnedValue.toFloat())
        anim.animateTo(
            targetValue = targetEarned.toFloat(),
            animationSpec = tween(durationMillis = 1200, easing = EaseOut)
        ) { earnedValue = value.toInt() }
    }
    LaunchedEffect(targetSpent) {
        val anim = Animatable(spentValue.toFloat())
        anim.animateTo(
            targetValue = targetSpent.toFloat(),
            animationSpec = tween(durationMillis = 1200, easing = EaseOut)
        ) { spentValue = value.toInt() }
    }

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
            Text(
                "TOTAL BALANCE",
                fontFamily = JakartaFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.4f),
                letterSpacing = 3.sp
            )
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
                    Text("1", color = CoinBg, fontWeight = FontWeight.Black, fontSize = 22.sp, fontFamily = OutfitFont)
                }
                Spacer(Modifier.width(16.dp))

                // Huge Balance Number
                Text(
                    text = balanceValue.toString(),
                    fontFamily = OutfitFont,
                    fontWeight = FontWeight.Black,
                    fontSize = 76.sp,
                    style = TextStyle(
                        brush = Brush.linearGradient(
                            colors = listOf(Color.White, CoinGoldLight, CoinGoldPrimary)
                        ),
                        shadow = androidx.compose.ui.graphics.Shadow(
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
    val infiniteTransition = rememberInfiniteTransition()
    val flameScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

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
                        "${state.streak?.currentStreak ?: 0} Day Streak",
                        fontFamily = JakartaFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                    Text(
                        "Best: ${state.streak?.longestStreak ?: 0} days",
                        fontFamily = JakartaFont,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.35f)
                    )
                }

                // Claim Badge
                if (state.claimStatus == ClaimStatus.AVAILABLE) {
                    val nextReward = vm.getNextClaimReward(context)
                    Button(
                        onClick = { vm.claimDaily(context) },
                        colors = ButtonDefaults.buttonColors(containerColor = CoinGoldPrimary),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("CLAIM +$nextReward", fontFamily = JakartaFont, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = CoinBg)
                    }
                } else if (state.claimStatus == ClaimStatus.ALREADY_CLAIMED || state.claimStatus == ClaimStatus.CLAIMED_NOW) {
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
            }

            Spacer(Modifier.height(20.dp))

            // Calendar Grid (Actual Dates)
            val days = listOf("S", "M", "T", "W", "T", "F", "S")
            val today = java.time.LocalDate.now()
            val currentDayOfWeek = today.dayOfWeek.value // 1 (Mon) to 7 (Sun)
            val sunday = today.minusDays((currentDayOfWeek % 7).toLong())
            
            val weekDates = (0..6).map { sunday.plusDays(it.toLong()) }
            val streakCount = state.streak?.currentStreak ?: 0
            val claimedToday = state.claimStatus == ClaimStatus.ALREADY_CLAIMED || state.claimStatus == ClaimStatus.CLAIMED_NOW
            val startCompletedDaysAgo = if (claimedToday) 0 else 1
            val endCompletedDaysAgo = startCompletedDaysAgo + streakCount - 1

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                days.forEachIndexed { index, day ->
                    val date = weekDates[index]
                    val isToday = date == today
                    val daysAgo = java.time.temporal.ChronoUnit.DAYS.between(date, today).toInt()
                    val isCompleted = daysAgo in startCompletedDaysAgo..endCompletedDaysAgo

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(day, fontFamily = JakartaFont, fontSize = 9.sp, color = Color.White.copy(alpha = 0.25f))
                        Spacer(Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(if (isCompleted) CoinGoldPrimary.copy(alpha = 0.2f) else Color.Transparent)
                                .border(
                                    1.dp,
                                    if (isCompleted) CoinGoldPrimary.copy(alpha = 0.3f) else if (isToday) Color.White.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.1f),
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
                                    fontSize = 12.sp,
                                    color = if (isToday) Color.White else Color.White.copy(alpha = 0.3f)
                                )
                            }

                            if (isToday) {
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
                "Complete today to keep your streak alive!",
                fontFamily = JakartaFont,
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.25f),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun AchievementsSection() {
    Column {
        Text("Achievements", fontFamily = JakartaFont, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
        Spacer(Modifier.height(16.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AchievementItem("Starter", "⭐", isUnlocked = true)
            AchievementItem("Striker", "⚡", isUnlocked = true, color = Color(0xFFA855F7))
            AchievementItem("On Fire", "🔥", isUnlocked = false)
            AchievementItem("Diamond", "💎", isUnlocked = false)
            AchievementItem("Legend", "👑", isUnlocked = false)
        }
    }
}

@Composable
private fun AchievementItem(name: String, icon: String, isUnlocked: Boolean, color: Color = CoinGoldPrimary) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (isUnlocked) color.copy(alpha = 0.15f) else Color(0xFF161622))
                .border(
                    1.dp,
                    if (isUnlocked) color.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.1f),
                    RoundedCornerShape(16.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                icon,
                fontSize = 28.sp,
                modifier = Modifier.scale(if (isUnlocked) 1f else 0.8f),
                color = if (isUnlocked) Color.White else Color.White.copy(alpha = 0.3f)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            name,
            fontFamily = JakartaFont,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            color = if (isUnlocked) color else Color.White.copy(alpha = 0.3f)
        )
    }
}

@Composable
private fun InviteFriendsSection(state: CoinState, vm: CoinViewModel, context: Context) {
    val profile = state.userProfile
    var referralCodeInput by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var isRedeeming by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition()
    val shimmerTranslateAnim by infiniteTransition.animateFloat(
        initialValue = -500f,
        targetValue = 1500f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
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
                // Shimmer Overlay
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
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("Referral Code", profile?.referralCode ?: "")
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Code copied!", Toast.LENGTH_SHORT).show()
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
                                    Toast.makeText(context, result.exceptionOrNull()?.message ?: "Failed", Toast.LENGTH_LONG).show()
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
private fun EarnCoinsSection(activity: android.app.Activity?, context: Context, vm: CoinViewModel, isAdLoading: Boolean, onAdLoadChange: (Boolean) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Earn Coins", fontFamily = JakartaFont, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.background(CoinGoldPrimary.copy(alpha = 0.15f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 2.dp)
            ) { Text("9 TASKS", fontFamily = JakartaFont, fontWeight = FontWeight.Bold, fontSize = 9.sp, color = CoinGoldPrimary) }
        }
        Spacer(Modifier.height(16.dp))

        // Featured Ad Task
        TaskItem(
            title = if (isAdLoading) "Loading Ad..." else "Watch Reward Ad",
            desc = "Watch a short ad to earn instantly",
            icon = Icons.Rounded.PlayCircle,
            reward = "+10",
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
                                Toast.makeText(context, "Congratulations! You received +10 Coins 🪙", Toast.LENGTH_LONG).show()
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

        // Standard Tasks
        val currentStreak = vm.state.value.streak?.currentStreak ?: 0
        val longestStreak = vm.state.value.streak?.longestStreak ?: 0
        val is7Eligible = currentStreak >= 7 || longestStreak >= 7
        val is30Eligible = currentStreak >= 30 || longestStreak >= 30
        val is7Claimed = vm.isStreakMilestoneClaimed(7, context)
        val is30Claimed = vm.isStreakMilestoneClaimed(30, context)

        TaskItem("Daily Login", "Come back every day", Icons.Rounded.EventAvailable, "+5")
        TaskItem("Watch 2 Episodes", "Enjoy your favorite shows (Daily)", Icons.Rounded.OndemandVideo, "+35")
        TaskItem("Download 2 Content", "Save for offline viewing (Daily)", Icons.Rounded.CloudDownload, "+20")
        TaskItem(
            title = "7-Day Streak Milestone",
            desc = when {
                is7Claimed -> "✓ 7-Day streak milestone bonus claimed!"
                is7Eligible -> "🎉 Reached! Tap to claim your +50 Bonus Coins!"
                else -> "Stay consistent for 7 days ($currentStreak/7 days)"
            },
            icon = Icons.Rounded.LocalFireDepartment,
            reward = when {
                is7Claimed -> "✓ Claimed"
                is7Eligible -> "CLAIM +50"
                else -> "+50"
            },
            isFeatured = is7Eligible && !is7Claimed,
            onClick = {
                if (is7Claimed) {
                    Toast.makeText(context, "✓ You have already claimed the 7-Day streak bonus!", Toast.LENGTH_SHORT).show()
                } else if (is7Eligible) {
                    vm.claimStreakMilestone(7, 50, context)
                } else {
                    val remaining = 7 - (currentStreak % 7)
                    Toast.makeText(context, "Claim daily for 7 days to unlock +50 Bonus Coins! ($remaining days remaining)", Toast.LENGTH_LONG).show()
                }
            }
        )
        TaskItem(
            title = "30-Day Streak Milestone",
            desc = when {
                is30Claimed -> "✓ 30-Day streak milestone bonus claimed!"
                is30Eligible -> "🏆 Reached! Tap to claim your +250 Bonus Coins!"
                else -> "A full month of dedication ($currentStreak/30 days)"
            },
            icon = Icons.Rounded.WorkspacePremium,
            reward = when {
                is30Claimed -> "✓ Claimed"
                is30Eligible -> "CLAIM +250"
                else -> "+250"
            },
            isFeatured = is30Eligible && !is30Claimed,
            onClick = {
                if (is30Claimed) {
                    Toast.makeText(context, "✓ You have already claimed the 30-Day streak bonus!", Toast.LENGTH_SHORT).show()
                } else if (is30Eligible) {
                    vm.claimStreakMilestone(30, 250, context)
                } else {
                    val remaining = 30 - (currentStreak % 30)
                    Toast.makeText(context, "Claim daily for 30 days to unlock +250 Bonus Coins! ($remaining days remaining)", Toast.LENGTH_LONG).show()
                }
            }
        )
        TaskItem("Refer a Friend", "Share your code", Icons.Rounded.Handshake, "+50")
        TaskItem("Post Comment", "Share your thoughts", Icons.Rounded.Forum, "+1")
        TaskItem("Report Issue", "Help us improve", Icons.Rounded.BugReport, "+20")
    }
}

@Composable
private fun TaskItem(title: String, desc: String, icon: androidx.compose.ui.graphics.vector.ImageVector, reward: String, isFeatured: Boolean = false, onClick: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .bounceClick(onClick = onClick)
            .background(CoinCard, RoundedCornerShape(16.dp))
            .border(1.dp, if (isFeatured) CoinGoldPrimary.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
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
            Text(desc, fontFamily = JakartaFont, fontSize = 12.sp, color = Color.White.copy(alpha = 0.4f))
        }
        Box(
            Modifier
                .background(CoinGoldPrimary.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                .border(1.dp, CoinGoldPrimary.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(reward, fontFamily = OutfitFont, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = CoinGoldPrimary)
        }
    }
}

@Composable
private fun CoinShopSection(state: CoinState, vm: CoinViewModel, context: Context) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Coin Shop", fontFamily = JakartaFont, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.ShoppingCart, null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(16.dp))

        // Ad Free active status
        val adFreeUntil = state.adFreeExpiry ?: AdManager.getAdFreeExpiry(context)
        if (!adFreeUntil.isNullOrEmpty()) {
            val expiry = AdManager.parseDateRobust(adFreeUntil)
            if (expiry != null && expiry.isAfter(Instant.now())) {
                val duration = Duration.between(Instant.now(), expiry)
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
                Spacer(Modifier.height(16.dp))
            }
        }

        ShopItemRow("Ad-Free 24h", "No ads for 24 hours", 100, ErrorRed) { vm.buyAdFree(1, 100, context) }
        ShopItemRow("Ad-Free 7d", "No ads for 7 days", 250, ErrorRed) { vm.buyAdFree(7, 250, context) }
        ShopItemRow("Priority Request", "Your content request goes first", 30, ErrorRed) { }
        ShopItemRow("VIP Badge", "Gold Badge + Ad-Free for 30 days", 250, Color(0xFFFFD700)) { vm.buyVipBadge("vip", 30, 250, context) }
        ShopItemRow("ELITE PRO", "Diamond Badge + 2x Coins & 1080p for 30 days", 500, CoinGoldPrimary) { vm.buyVipBadge("elite_pro", 30, 500, context) }
        ShopItemRow("Double Coin Boost", "2x coins for 24 hours", 100, Color(0xFF06B6D4)) { vm.buyDoubleCoinBoost(context) }
    }
}

@Composable
private fun ShopItemRow(title: String, desc: String, price: Int, color: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .bounceClick(onClick = onClick)
            .background(CoinCard, RoundedCornerShape(16.dp))
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontFamily = JakartaFont, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White)
            Text(desc, fontFamily = JakartaFont, fontSize = 12.sp, color = Color.White.copy(alpha = 0.4f))
        }
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = color.copy(alpha = 0.15f)),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            modifier = Modifier.border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
        ) {
            Text("🪙 $price", fontFamily = OutfitFont, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = color)
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
