package com.nexiplay.app.ui.screens.coins

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexiplay.app.data.SupabaseClient
import com.nexiplay.app.data.model.CoinBalance
import com.nexiplay.app.data.model.CoinStreak
import com.nexiplay.app.data.model.CoinTransaction
import com.nexiplay.app.data.model.UserProfile
import com.nexiplay.app.data.util.AdManager
import com.nexiplay.app.data.util.CoinRewardHelper
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

data class CoinState(
    val balance: CoinBalance? = null,
    val streak: CoinStreak? = null,
    val userProfile: UserProfile? = null,
    val userId: String? = null,
    val isLoggedIn: Boolean = false,
    val isLoading: Boolean = true,
    val isPurchasing: Boolean = false,
    val claimStatus: ClaimStatus = ClaimStatus.CHECKING,
    val claimMessage: String? = null,
    val lastReward: Int = 0,
    val profileError: String? = null,
    val showPurchaseDialog: Boolean = false,
    val dialogTitle: String = "Purchase Successful!",
    val isErrorDialog: Boolean = false,
    val purchaseSuccessMessage: String? = null,
    val adFreeExpiry: String? = null,
    val lastClaimLocalDate: String? = null,
)

enum class ClaimStatus {
    CHECKING,        // Initial - checking DB
    AVAILABLE,       // Can claim today
    CLAIMING,        // In progress
    CLAIMED_NOW,     // Just claimed this session
    ALREADY_CLAIMED, // Already claimed today (from DB)
    ERROR,
}

class CoinViewModel : ViewModel() {
    private val _state = MutableStateFlow(CoinState())
    val state = _state.asStateFlow()

    init {
        initialize()
    }

    private fun initialize() {
        viewModelScope.launch {
            try {
                SupabaseClient.main.auth.awaitInitialization()
            } catch (_: Exception) {}

            val user = SupabaseClient.main.auth.currentUserOrNull()
            if (user == null) {
                _state.value = CoinState(
                    isLoggedIn = false,
                    isLoading = false,
                    claimStatus = ClaimStatus.AVAILABLE
                )
                return@launch
            }

            _state.value = _state.value.copy(userId = user.id, isLoggedIn = true)
            loadData()
        }
    }

    /**
     * Parses the user's last daily claim date into a LocalDate in the user's local timezone.
     * Prefers `lastLoginDate` (YYYY-MM-DD local date) and falls back to `lastDailyClaim` (ISO timestamp).
     */
    fun parseLastClaimLocalDate(streak: CoinStreak?): LocalDate? {
        if (streak == null) return null
        val loginDate = streak.lastLoginDate?.trim()?.takeIf { it.length >= 10 }?.let {
            try { LocalDate.parse(it.substring(0, 10)) } catch (_: Exception) { null }
        }
        val claimDate = streak.lastDailyClaim?.trim()?.takeIf { it.isNotEmpty() }?.let { raw ->
            try {
                val instant = AdManager.parseDateRobust(raw)
                if (instant != null) {
                    instant.atZone(ZoneId.systemDefault()).toLocalDate()
                } else if (raw.length >= 10) {
                    LocalDate.parse(raw.substring(0, 10))
                } else null
            } catch (_: Exception) {
                try {
                    if (raw.length >= 10) LocalDate.parse(raw.substring(0, 10)) else null
                } catch (_: Exception) { null }
            }
        }
        return when {
            loginDate != null && claimDate != null -> maxOf(loginDate, claimDate)
            loginDate != null -> loginDate
            else -> claimDate
        }
    }

    fun loadData(context: Context? = null) {
        viewModelScope.launch {
            try {
                SupabaseClient.main.auth.awaitInitialization()
            } catch (_: Exception) {}

            val currentUser = SupabaseClient.main.auth.currentUserOrNull()
            val uid = currentUser?.id ?: _state.value.userId
            if (uid == null) {
                _state.value = _state.value.copy(
                    userId = null,
                    isLoggedIn = false,
                    isLoading = false,
                    claimStatus = ClaimStatus.AVAILABLE
                )
                return@launch
            }

            _state.value = _state.value.copy(userId = uid, isLoggedIn = true, isLoading = true)

            try {
                // 1. Fetch or initialize balance
                var balance = try {
                    SupabaseClient.main.from("coin_balances")
                        .select { filter { eq("user_id", uid) } }
                        .decodeSingleOrNull<CoinBalance>()
                } catch (_: Exception) { null }

                if (balance == null) {
                    try {
                        val initBalJson = buildJsonObject {
                            put("user_id", JsonPrimitive(uid))
                            put("balance", JsonPrimitive(0))
                            put("total_earned", JsonPrimitive(0))
                            put("total_spent", JsonPrimitive(0))
                        }
                        SupabaseClient.main.from("coin_balances").insert(initBalJson)
                        balance = CoinBalance(userId = uid, balance = 0, totalEarned = 0, totalSpent = 0)
                    } catch (_: Exception) {
                        balance = CoinBalance(userId = uid, balance = 0, totalEarned = 0, totalSpent = 0)
                    }
                }

                // 2. Fetch or initialize streak
                var streak = try {
                    SupabaseClient.main.from("coin_streaks")
                        .select { filter { eq("user_id", uid) } }
                        .decodeSingleOrNull<CoinStreak>()
                } catch (_: Exception) { null }

                if (streak == null) {
                    try {
                        val initStreakJson = buildJsonObject {
                            put("user_id", JsonPrimitive(uid))
                            put("current_streak", JsonPrimitive(0))
                            put("longest_streak", JsonPrimitive(0))
                        }
                        SupabaseClient.main.from("coin_streaks").insert(initStreakJson)
                        streak = CoinStreak(userId = uid, currentStreak = 0, longestStreak = 0)
                    } catch (_: Exception) {
                        streak = CoinStreak(userId = uid, currentStreak = 0, longestStreak = 0)
                    }
                }

                // 3. Fetch or initialize User Profile
                var profile: UserProfile? = null
                var profileErrorMsg: String? = null
                try {
                    profile = SupabaseClient.main.from("profiles")
                        .select { filter { eq("id", uid) } }
                        .decodeSingleOrNull<UserProfile>()

                    if (profile == null) {
                        val newCode = java.util.UUID.randomUUID().toString().substring(0, 8).uppercase()
                        val newProfileJson = buildJsonObject {
                            put("id", JsonPrimitive(uid))
                            currentUser?.email?.let { put("email", JsonPrimitive(it)) }
                            put("referral_code", JsonPrimitive(newCode))
                        }
                        try {
                            SupabaseClient.main.from("profiles").insert(newProfileJson)
                            profile = SupabaseClient.main.from("profiles")
                                .select { filter { eq("id", uid) } }
                                .decodeSingleOrNull<UserProfile>()
                        } catch (e: Exception) {
                            profileErrorMsg = com.nexiplay.app.ui.util.UserFacingError.from(e, "Couldn't prepare your coin profile.")
                            android.util.Log.e("CoinViewModel", profileErrorMsg)
                        }
                    } else if (profile.referralCode.isNullOrBlank()) {
                        val newCode = java.util.UUID.randomUUID().toString().substring(0, 8).uppercase()
                        try {
                            val updatedJson = buildJsonObject {
                                put("referral_code", JsonPrimitive(newCode))
                            }
                            SupabaseClient.main.from("profiles").update(updatedJson) {
                                filter { eq("id", uid) }
                            }
                            profile = profile.copy(referralCode = newCode)
                        } catch (e: Exception) {
                            profileErrorMsg = com.nexiplay.app.ui.util.UserFacingError.from(e, "Couldn't update your referral code.")
                            android.util.Log.e("CoinViewModel", profileErrorMsg)
                        }
                    }
                } catch (e: Exception) {
                    profileErrorMsg = com.nexiplay.app.ui.util.UserFacingError.from(e, "Couldn't load your coin profile.")
                    android.util.Log.e("CoinViewModel", "Failed to fetch profile: ${e.stackTraceToString()}")
                }

                // Sync VIP / Elite / Ad-Free / 2x Boost status with AdManager
                if (context != null && profile != null) {
                    AdManager.syncProfilePerks(
                        context = context,
                        vipBadge = profile.vipBadge,
                        vipBadgeExpires = profile.vipBadgeExpires,
                        adFreeUntil = profile.adFreeUntil,
                        doubleBoostUntilStr = profile.doubleBoostUntil
                    )
                    // Sync milestone claims from DB to local prefs
                    val milestonePrefs = context.getSharedPreferences("NexiPlayMilestones", Context.MODE_PRIVATE)
                    if (!streak?.streak7ClaimedAt.isNullOrBlank()) {
                        milestonePrefs.edit().putBoolean("claimed_7_$uid", true).apply()
                    }
                    if (!streak?.streak30ClaimedAt.isNullOrBlank()) {
                        milestonePrefs.edit().putBoolean("claimed_30_$uid", true).apply()
                    }

                    // Sync today's watch/download reward status from coin_transactions if needed
                    try {
                        val todayPrefix = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                        val recentTxs = SupabaseClient.main.from("coin_transactions")
                            .select {
                                filter {
                                    eq("user_id", uid)
                                }
                            }
                            .decodeList<CoinTransaction>()
                        recentTxs.forEach { tx ->
                            val createdDate = tx.createdAt?.let { raw ->
                                AdManager.parseDateRobust(raw)?.atZone(ZoneId.systemDefault())?.toLocalDate()?.toString()
                                    ?: raw.take(10)
                            }
                            if (createdDate == todayPrefix) {
                                if (tx.type == "daily_watch" || tx.type == "daily_download") {
                                    CoinRewardHelper.markDailyRewardedFromServer(context, uid, tx.type)
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }

                // 4. Determine if already claimed today and compute effective streak
                val todayDate = LocalDate.now()
                val lastClaimDate = parseLastClaimLocalDate(streak)
                val daysBetween = if (lastClaimDate != null) ChronoUnit.DAYS.between(lastClaimDate, todayDate) else 999L
                val alreadyClaimed = lastClaimDate != null && daysBetween <= 0L

                // If user missed 1 or more full days (daysBetween > 1), their active streak before claiming today is 0
                val rawStreak = streak?.currentStreak ?: 0
                val effectiveStreak = when {
                    alreadyClaimed -> maxOf(1, rawStreak)
                    daysBetween == 1L -> rawStreak
                    else -> 0
                }
                val displayStreak = streak?.copy(currentStreak = effectiveStreak)

                _state.value = _state.value.copy(
                    balance = balance,
                    streak = displayStreak,
                    userProfile = profile,
                    profileError = profileErrorMsg,
                    isLoading = false,
                    claimStatus = if (alreadyClaimed) ClaimStatus.ALREADY_CLAIMED else ClaimStatus.AVAILABLE,
                    adFreeExpiry = profile?.adFreeUntil ?: AdManager.getAdFreeExpiry(context ?: return@launch),
                    lastClaimLocalDate = lastClaimDate?.toString()
                )

            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    claimStatus = ClaimStatus.ERROR,
                    claimMessage = com.nexiplay.app.ui.util.UserFacingError.from(e, "Couldn't load your coin balance. Please try again.")
                )
                android.util.Log.e("CoinViewModel", "Critical error in loadData: ${e.stackTraceToString()}")
            }
        }
    }

    fun getNextClaimReward(context: Context): Int {
        val streak = _state.value.streak
        val todayDate = LocalDate.now()
        val lastClaimDate = parseLastClaimLocalDate(streak)
        val oldStreak = streak?.currentStreak ?: 0

        val nextStreak = if (lastClaimDate == null) {
            1
        } else {
            val daysBetween = ChronoUnit.DAYS.between(lastClaimDate, todayDate)
            when (daysBetween) {
                0L -> maxOf(1, oldStreak)
                1L -> oldStreak + 1
                else -> 1
            }
        }

        val baseReward = when {
            nextStreak > 0 && nextStreak % 30 == 0 -> 250
            nextStreak > 0 && nextStreak % 7 == 0 -> 50
            else -> 5
        }
        return if (isBoostActive(context)) baseReward * 2 else baseReward
    }

    fun isStreakMilestoneClaimed(milestoneDays: Int, context: Context): Boolean {
        val uid = _state.value.userId ?: return false
        val streak = _state.value.streak
        if (milestoneDays == 7 && !streak?.streak7ClaimedAt.isNullOrBlank()) return true
        if (milestoneDays == 30 && !streak?.streak30ClaimedAt.isNullOrBlank()) return true

        val prefs = context.getSharedPreferences("NexiPlayMilestones", Context.MODE_PRIVATE)
        return prefs.getBoolean("claimed_${milestoneDays}_$uid", false)
    }

    fun claimStreakMilestone(milestoneDays: Int, rewardAmount: Int, context: Context) {
        viewModelScope.launch {
            val uid = _state.value.userId ?: SupabaseClient.main.auth.currentUserOrNull()?.id ?: return@launch
            val streak = _state.value.streak
            val curStreak = streak?.currentStreak ?: 0
            val bestStreak = streak?.longestStreak ?: 0
            val reached = curStreak >= milestoneDays || bestStreak >= milestoneDays

            if (!reached) {
                Toast.makeText(context, "You need a $milestoneDays-day streak to claim this!", Toast.LENGTH_SHORT).show()
                return@launch
            }

            if (isStreakMilestoneClaimed(milestoneDays, context)) {
                Toast.makeText(context, "You have already claimed this milestone reward!", Toast.LENGTH_SHORT).show()
                return@launch
            }

            try {
                val finalAmount = if (isBoostActive(context)) rewardAmount * 2 else rewardAmount
                val freshBal = try {
                    SupabaseClient.main.from("coin_balances")
                        .select { filter { eq("user_id", uid) } }
                        .decodeSingleOrNull<CoinBalance>()
                } catch (_: Exception) { null }

                val currentBal = freshBal?.balance ?: _state.value.balance?.balance ?: 0
                val currentEarned = freshBal?.totalEarned ?: _state.value.balance?.totalEarned ?: 0
                val currentSpent = freshBal?.totalSpent ?: _state.value.balance?.totalSpent ?: 0

                val newBal = currentBal + finalAmount
                val newEarned = currentEarned + finalAmount

                if (freshBal == null) {
                    val insertBalJson = buildJsonObject {
                        put("user_id", JsonPrimitive(uid))
                        put("balance", JsonPrimitive(newBal))
                        put("total_earned", JsonPrimitive(newEarned))
                        put("total_spent", JsonPrimitive(currentSpent))
                    }
                    SupabaseClient.main.from("coin_balances").insert(insertBalJson)
                } else {
                    val balanceJson = buildJsonObject {
                        put("balance", JsonPrimitive(newBal))
                        put("total_earned", JsonPrimitive(newEarned))
                        put("total_spent", JsonPrimitive(currentSpent))
                    }
                    SupabaseClient.main.from("coin_balances").update(balanceJson) { filter { eq("user_id", uid) } }
                }

                // Mark milestone claimed in coin_streaks table as well
                val nowIso = Instant.now().toString()
                val colName = if (milestoneDays == 7) "streak_7_claimed_at" else "streak_30_claimed_at"
                try {
                    val streakUpdate = buildJsonObject {
                        put(colName, JsonPrimitive(nowIso))
                    }
                    SupabaseClient.main.from("coin_streaks").update(streakUpdate) { filter { eq("user_id", uid) } }
                } catch (_: Exception) {}

                val txJson = buildJsonObject {
                    put("user_id", JsonPrimitive(uid))
                    put("amount", JsonPrimitive(finalAmount))
                    put("type", JsonPrimitive("streak_milestone_$milestoneDays"))
                    put("description", JsonPrimitive("🎉 Claimed $milestoneDays-Day Streak Milestone Reward!"))
                }
                try { SupabaseClient.main.from("coin_transactions").insert(txJson) } catch (_: Exception) {}

                val prefs = context.getSharedPreferences("NexiPlayMilestones", Context.MODE_PRIVATE)
                prefs.edit().putBoolean("claimed_${milestoneDays}_$uid", true).apply()

                _state.value = _state.value.copy(
                    balance = CoinBalance(
                        userId = uid,
                        balance = newBal,
                        totalEarned = newEarned,
                        totalSpent = currentSpent
                    ),
                    showPurchaseDialog = true,
                    dialogTitle = "Milestone Unlocked! 🎉",
                    isErrorDialog = false,
                    purchaseSuccessMessage = "Awesome! You earned +$finalAmount coins for reaching the $milestoneDays-Day Streak Milestone!"
                )

                delay(300)
                loadData(context)
            } catch (e: Exception) {
                Toast.makeText(context, com.nexiplay.app.ui.util.UserFacingError.from(e, "Couldn't claim this reward. Please try again."), Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun claimDaily(context: Context) {
        viewModelScope.launch {
            try { SupabaseClient.main.auth.awaitInitialization() } catch (_: Exception) {}
            val uid = _state.value.userId ?: SupabaseClient.main.auth.currentUserOrNull()?.id ?: return@launch
            if (_state.value.claimStatus == ClaimStatus.CLAIMING ||
                _state.value.claimStatus == ClaimStatus.ALREADY_CLAIMED ||
                _state.value.claimStatus == ClaimStatus.CLAIMED_NOW
            ) return@launch

            _state.value = _state.value.copy(claimStatus = ClaimStatus.CLAIMING)

            try {
                val todayDate = LocalDate.now()
                val today = todayDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
                val nowIso = Instant.now().toString()

                // ── Double-check server-side ──
                val freshStreak = try {
                    SupabaseClient.main.from("coin_streaks")
                        .select { filter { eq("user_id", uid) } }
                        .decodeSingleOrNull<CoinStreak>()
                } catch (_: Exception) { null }

                val lastClaimDate = parseLastClaimLocalDate(freshStreak)
                if (lastClaimDate != null && !lastClaimDate.isBefore(todayDate)) {
                    // Already claimed today - sync UI
                    _state.value = _state.value.copy(
                        streak = freshStreak,
                        claimStatus = ClaimStatus.ALREADY_CLAIMED,
                        lastClaimLocalDate = today
                    )
                    Toast.makeText(context, "✓ You've already claimed today's daily reward!", Toast.LENGTH_SHORT).show()
                    loadData(context)
                    return@launch
                }

                // ── Calculate streak ──
                val oldStreak = freshStreak?.currentStreak ?: 0
                val newStreak = if (lastClaimDate == null) {
                    1
                } else {
                    val daysBetween = ChronoUnit.DAYS.between(lastClaimDate, todayDate)
                    when {
                        daysBetween <= 0L -> maxOf(1, oldStreak)
                        daysBetween == 1L -> oldStreak + 1
                        else -> 1
                    }
                }
                val longestStreak = maxOf(newStreak, freshStreak?.longestStreak ?: 0)

                // ── Calculate reward ──
                val baseReward = when {
                    newStreak > 0 && newStreak % 30 == 0 -> 250
                    newStreak > 0 && newStreak % 7 == 0 -> 50
                    else -> 5
                }
                val boosted = isBoostActive(context)
                val reward = if (boosted) baseReward * 2 else baseReward

                // ── 1. Update / Insert coin_balances ──
                val freshBal = try {
                    SupabaseClient.main.from("coin_balances")
                        .select { filter { eq("user_id", uid) } }
                        .decodeSingleOrNull<CoinBalance>()
                } catch (_: Exception) { null }

                val currentBal = freshBal?.balance ?: _state.value.balance?.balance ?: 0
                val currentEarned = freshBal?.totalEarned ?: _state.value.balance?.totalEarned ?: 0
                val currentSpent = freshBal?.totalSpent ?: _state.value.balance?.totalSpent ?: 0

                val newBal = currentBal + reward
                val newEarned = currentEarned + reward

                if (freshBal == null) {
                    val insertBalJson = buildJsonObject {
                        put("user_id", JsonPrimitive(uid))
                        put("balance", JsonPrimitive(newBal))
                        put("total_earned", JsonPrimitive(newEarned))
                        put("total_spent", JsonPrimitive(currentSpent))
                    }
                    SupabaseClient.main.from("coin_balances").insert(insertBalJson)
                } else {
                    val balanceJson = buildJsonObject {
                        put("balance", JsonPrimitive(newBal))
                        put("total_earned", JsonPrimitive(newEarned))
                        put("total_spent", JsonPrimitive(currentSpent))
                    }
                    SupabaseClient.main.from("coin_balances").update(balanceJson) { filter { eq("user_id", uid) } }
                }

                // ── 2. Insert coin_transaction ──
                val desc = buildString {
                    append(
                        when {
                            newStreak > 0 && newStreak % 30 == 0 -> "🎉 30-day streak bonus ($newStreak days)!"
                            newStreak > 0 && newStreak % 7 == 0 -> "🔥 7-day streak bonus ($newStreak days)!"
                            else -> "Daily login reward (Day $newStreak)"
                        }
                    )
                    if (boosted) append(" (2x Boost!)")
                }
                val txJson = buildJsonObject {
                    put("user_id", JsonPrimitive(uid))
                    put("amount", JsonPrimitive(reward))
                    put("type", JsonPrimitive("daily_login"))
                    put("description", JsonPrimitive(desc))
                }
                try {
                    SupabaseClient.main.from("coin_transactions").insert(txJson)
                } catch (e: Exception) {
                    android.util.Log.e("CoinViewModel", "Tx insert error: ${e.message}")
                }

                // ── 3. Update / Insert coin_streaks ──
                if (freshStreak == null) {
                    val insertStreakJson = buildJsonObject {
                        put("user_id", JsonPrimitive(uid))
                        put("current_streak", JsonPrimitive(newStreak))
                        put("longest_streak", JsonPrimitive(longestStreak))
                        put("last_login_date", JsonPrimitive(today))
                        put("last_daily_claim", JsonPrimitive(nowIso))
                    }
                    SupabaseClient.main.from("coin_streaks").insert(insertStreakJson)
                } else {
                    val streakJson = buildJsonObject {
                        put("current_streak", JsonPrimitive(newStreak))
                        put("longest_streak", JsonPrimitive(longestStreak))
                        put("last_login_date", JsonPrimitive(today))
                        put("last_daily_claim", JsonPrimitive(nowIso))
                    }
                    SupabaseClient.main.from("coin_streaks").update(streakJson) { filter { eq("user_id", uid) } }
                }

                // ── 4. Insert Notification ──
                val notifJson = buildJsonObject {
                    put("user_id", JsonPrimitive(uid))
                    put("title", JsonPrimitive("Daily Reward Claimed! 🪙"))
                    put("message", JsonPrimitive("You earned +$reward coins! $desc"))
                    put("type", JsonPrimitive("reward"))
                }
                try {
                    SupabaseClient.main.from("notifications").insert(notifJson)
                } catch (_: Exception) {}

                // ── 5. Update local state immediately ──
                _state.value = _state.value.copy(
                    balance = CoinBalance(
                        userId = uid,
                        balance = newBal,
                        totalEarned = newEarned,
                        totalSpent = currentSpent,
                    ),
                    streak = CoinStreak(
                        userId = uid,
                        currentStreak = newStreak,
                        longestStreak = longestStreak,
                        lastLoginDate = today,
                        lastDailyClaim = nowIso,
                        streak7ClaimedAt = freshStreak?.streak7ClaimedAt,
                        streak30ClaimedAt = freshStreak?.streak30ClaimedAt
                    ),
                    claimStatus = ClaimStatus.CLAIMED_NOW,
                    lastReward = reward,
                    claimMessage = desc,
                    lastClaimLocalDate = today,
                    showPurchaseDialog = true,
                    dialogTitle = "Daily Reward Claimed! 🔥",
                    isErrorDialog = false,
                    purchaseSuccessMessage = "You earned +$reward Coins! ($desc)\nCurrent Streak: $newStreak ${if (newStreak == 1) "day" else "days"}."
                )

                delay(300)
                loadData(context)

            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    claimStatus = ClaimStatus.AVAILABLE,
                    claimMessage = com.nexiplay.app.ui.util.UserFacingError.from(e, "Couldn't claim your reward. Please try again."),
                )
                Toast.makeText(context, "Couldn't claim daily reward. Please check your connection and try again.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun claimAdReward(amount: Int, context: Context) {
        viewModelScope.launch {
            try { SupabaseClient.main.auth.awaitInitialization() } catch (_: Exception) {}
            val uid = _state.value.userId ?: SupabaseClient.main.auth.currentUserOrNull()?.id ?: return@launch
            try {
                val boosted = isBoostActive(context)
                val finalAmount = if (boosted) amount * 2 else amount

                // ── 1. Fetch fresh coin_balances ──
                val freshBal = try {
                    SupabaseClient.main.from("coin_balances")
                        .select { filter { eq("user_id", uid) } }
                        .decodeSingleOrNull<CoinBalance>()
                } catch (_: Exception) { null }

                val currentBal = freshBal?.balance ?: _state.value.balance?.balance ?: 0
                val currentEarned = freshBal?.totalEarned ?: _state.value.balance?.totalEarned ?: 0
                val currentSpent = freshBal?.totalSpent ?: _state.value.balance?.totalSpent ?: 0

                if (freshBal == null) {
                    val insertJson = buildJsonObject {
                        put("user_id", JsonPrimitive(uid))
                        put("balance", JsonPrimitive(finalAmount))
                        put("total_earned", JsonPrimitive(finalAmount))
                        put("total_spent", JsonPrimitive(0))
                    }
                    SupabaseClient.main.from("coin_balances").insert(insertJson)
                } else {
                    val balanceJson = buildJsonObject {
                        put("balance", JsonPrimitive(currentBal + finalAmount))
                        put("total_earned", JsonPrimitive(currentEarned + finalAmount))
                        put("total_spent", JsonPrimitive(currentSpent))
                    }
                    SupabaseClient.main.from("coin_balances").update(balanceJson) { filter { eq("user_id", uid) } }
                }

                // ── 2. Insert coin_transaction ──
                val txJson = buildJsonObject {
                    put("user_id", JsonPrimitive(uid))
                    put("amount", JsonPrimitive(finalAmount))
                    put("type", JsonPrimitive("reward_ad"))
                    put("description", JsonPrimitive(if (boosted) "Watched a reward ad (2x Boost!)" else "Watched a reward ad"))
                }
                try { SupabaseClient.main.from("coin_transactions").insert(txJson) } catch (_: Exception) {}

                // ── 3. Update local state immediately ──
                _state.value = _state.value.copy(
                    balance = CoinBalance(
                        userId = uid,
                        balance = currentBal + finalAmount,
                        totalEarned = currentEarned + finalAmount,
                        totalSpent = currentSpent,
                    ),
                    lastReward = finalAmount,
                    claimMessage = "You earned +$finalAmount coins from watching an ad!",
                )

                delay(300)
                loadData(context)
            } catch (e: Exception) {
                android.util.Log.e("CoinViewModel", "claimAdReward error: ${e.message}")
            }
        }
    }

    suspend fun redeemReferralCode(code: String): Result<String> {
        val uid = _state.value.userId ?: SupabaseClient.main.auth.currentUserOrNull()?.id
            ?: return Result.failure(Exception("Not logged in"))

        return try {
            val postgrest = SupabaseClient.main.pluginManager.getPlugin(io.github.jan.supabase.postgrest.Postgrest)
            postgrest.rpc<JsonObject>(
                function = "redeem_referral",
                parameters = buildJsonObject {
                    put("ref_code", JsonPrimitive(code.trim().uppercase()))
                }
            )

            loadData()
            Result.success("Success! You received 5 coins, and your friend received 50 coins.")
        } catch (e: Exception) {
            val errorMsg = com.nexiplay.app.ui.util.UserFacingError.from(e, "Something went wrong. Please try again.")
            Result.failure(IllegalStateException(errorMsg))
        }
    }

    suspend fun submitContactMessage(name: String, email: String, subject: String, message: String): Result<String> {
        return try {
            val postgrest = SupabaseClient.main.pluginManager.getPlugin(io.github.jan.supabase.postgrest.Postgrest)
            postgrest.rpc(
                function = "submit_contact_message",
                parameters = buildJsonObject {
                    put("p_name", JsonPrimitive(name))
                    put("p_email", JsonPrimitive(email))
                    put("p_subject", JsonPrimitive(subject))
                    put("p_message", JsonPrimitive(message))
                }
            )

            loadData()
            Result.success("Message sent successfully!")
        } catch (e: Exception) {
            Result.failure(IllegalStateException(com.nexiplay.app.ui.util.UserFacingError.from(e, "Couldn't send your message. Please try again.")))
        }
    }

    private fun showInsufficientCoinsDialog(currentBal: Int, cost: Int, itemName: String, context: Context) {
        val needed = maxOf(1, cost - currentBal)
        Toast.makeText(context, "Not enough coins! You need $needed more 🪙", Toast.LENGTH_SHORT).show()
        _state.value = _state.value.copy(
            isPurchasing = false,
            showPurchaseDialog = true,
            dialogTitle = "Not Enough Coins 🪙",
            isErrorDialog = true,
            purchaseSuccessMessage = "You tried to unlock $itemName ($cost 🪙), but your balance is $currentBal 🪙.\n\nYou need $needed more coins! Claim your daily login, watch 2 episodes (+35 🪙), or watch a reward ad to earn more."
        )
    }

    fun buyAdFree(days: Int, cost: Int, context: Context) {
        if (_state.value.isPurchasing) return
        viewModelScope.launch {
            try { SupabaseClient.main.auth.awaitInitialization() } catch (_: Exception) {}
            val uid = _state.value.userId ?: SupabaseClient.main.auth.currentUserOrNull()?.id ?: return@launch
            _state.value = _state.value.copy(isPurchasing = true)

            try {
                // Fetch fresh balance from DB first
                val freshBal = try {
                    SupabaseClient.main.from("coin_balances")
                        .select { filter { eq("user_id", uid) } }
                        .decodeSingleOrNull<CoinBalance>()
                } catch (_: Exception) { null }

                val currentBal = freshBal?.balance ?: _state.value.balance?.balance ?: 0
                val currentEarned = freshBal?.totalEarned ?: _state.value.balance?.totalEarned ?: 0
                val currentSpent = freshBal?.totalSpent ?: _state.value.balance?.totalSpent ?: 0

                if (currentBal < cost) {
                    showInsufficientCoinsDialog(currentBal, cost, "Ad-Free (${if (days == 1) "24h" else "${days}d"})", context)
                    return@launch
                }

                // 1. Deduct coins from balances
                val balanceJson = buildJsonObject {
                    put("balance", JsonPrimitive(currentBal - cost))
                    put("total_earned", JsonPrimitive(currentEarned))
                    put("total_spent", JsonPrimitive(currentSpent + cost))
                }
                SupabaseClient.main.from("coin_balances").update(balanceJson) { filter { eq("user_id", uid) } }

                // 2. Extend ad_free_until from current active expiry (if any) so we never shorten an existing pass
                val now = Instant.now()
                val existingExpiry = AdManager.parseDateRobust(_state.value.userProfile?.adFreeUntil ?: AdManager.getAdFreeExpiry(context))
                val baseInstant = if (existingExpiry != null && existingExpiry.isAfter(now)) existingExpiry else now
                val newExpiry = baseInstant.plus(java.time.Duration.ofDays(days.toLong())).toString()

                val profileJson = buildJsonObject {
                    put("ad_free_until", JsonPrimitive(newExpiry))
                }
                SupabaseClient.main.from("profiles").update(profileJson) { filter { eq("id", uid) } }

                // 3. Insert transaction
                val txJson = buildJsonObject {
                    put("user_id", JsonPrimitive(uid))
                    put("amount", JsonPrimitive(-cost))
                    put("type", JsonPrimitive("shop_purchase"))
                    put("description", JsonPrimitive("Purchased Ad-Free for ${if (days == 1) "24 hours" else "$days days"}"))
                }
                try { SupabaseClient.main.from("coin_transactions").insert(txJson) } catch (_: Exception) {}

                // 4. Sync AdManager immediately
                AdManager.syncProfilePerks(
                    context = context,
                    vipBadge = _state.value.userProfile?.vipBadge ?: AdManager.userBadgeType,
                    vipBadgeExpires = _state.value.userProfile?.vipBadgeExpires ?: AdManager.badgeExpiresAt,
                    adFreeUntil = newExpiry,
                    doubleBoostUntilStr = _state.value.userProfile?.doubleBoostUntil ?: AdManager.doubleBoostUntil
                )

                _state.value = _state.value.copy(
                    isPurchasing = false,
                    balance = CoinBalance(uid, currentBal - cost, currentEarned, currentSpent + cost),
                    userProfile = _state.value.userProfile?.copy(adFreeUntil = newExpiry),
                    showPurchaseDialog = true,
                    dialogTitle = "Ad-Free Unlocked! 🛡️",
                    isErrorDialog = false,
                    purchaseSuccessMessage = "Ad-Free streaming is now active for ${if (days == 1) "24 hours" else "$days days"}! Enjoy NexiPlay without any interruptions.",
                    adFreeExpiry = newExpiry
                )

                loadData(context)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isPurchasing = false)
                Toast.makeText(context, com.nexiplay.app.ui.util.UserFacingError.from(e, "Purchase failed. Please try again."), Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun buyVipBadge(tier: String, days: Int, cost: Int, context: Context) {
        if (_state.value.isPurchasing) return
        viewModelScope.launch {
            try { SupabaseClient.main.auth.awaitInitialization() } catch (_: Exception) {}
            val uid = _state.value.userId ?: SupabaseClient.main.auth.currentUserOrNull()?.id ?: return@launch
            _state.value = _state.value.copy(isPurchasing = true)

            val isEliteTier = tier.lowercase().contains("elite") || tier.lowercase() == "gold_vip"
            val title = if (isEliteTier) "ELITE PRO" else "VIP Badge"

            try {
                // Fetch fresh balance from DB first
                val freshBal = try {
                    SupabaseClient.main.from("coin_balances")
                        .select { filter { eq("user_id", uid) } }
                        .decodeSingleOrNull<CoinBalance>()
                } catch (_: Exception) { null }

                val currentBal = freshBal?.balance ?: _state.value.balance?.balance ?: 0
                val currentEarned = freshBal?.totalEarned ?: _state.value.balance?.totalEarned ?: 0
                val currentSpent = freshBal?.totalSpent ?: _state.value.balance?.totalSpent ?: 0

                if (currentBal < cost) {
                    showInsufficientCoinsDialog(currentBal, cost, title, context)
                    return@launch
                }

                // 1. Deduct coins from balances
                val balanceJson = buildJsonObject {
                    put("balance", JsonPrimitive(currentBal - cost))
                    put("total_earned", JsonPrimitive(currentEarned))
                    put("total_spent", JsonPrimitive(currentSpent + cost))
                }
                SupabaseClient.main.from("coin_balances").update(balanceJson) { filter { eq("user_id", uid) } }

                // 2. Calculate new expiry (extend if same/existing active badge)
                val now = Instant.now()
                val currentProfile = _state.value.userProfile
                val existingBadgeExp = AdManager.parseDateRobust(currentProfile?.vipBadgeExpires ?: AdManager.badgeExpiresAt)
                val baseBadgeInstant = if (existingBadgeExp != null && existingBadgeExp.isAfter(now)) existingBadgeExp else now
                val newExpiryInstant = baseBadgeInstant.plus(java.time.Duration.ofDays(days.toLong()))
                val newExpiry = newExpiryInstant.toString()

                val existingAdFreeExp = AdManager.parseDateRobust(currentProfile?.adFreeUntil ?: AdManager.getAdFreeExpiry(context))
                val newAdFreeExpiry = if (existingAdFreeExp != null && existingAdFreeExp.isAfter(newExpiryInstant)) {
                    existingAdFreeExp.toString()
                } else {
                    newExpiry
                }

                // If buying Elite Pro, stay prevents overwriting if already higher; also activate 30-day 2x Coin Boost!
                val finalTier = if (!isEliteTier && AdManager.isUserElite()) {
                    currentProfile?.vipBadge ?: "elite_pro"
                } else {
                    tier
                }

                val profileJson = buildJsonObject {
                    put("vip_badge", JsonPrimitive(finalTier))
                    put("vip_badge_expires", JsonPrimitive(newExpiry))
                    put("ad_free_until", JsonPrimitive(newAdFreeExpiry))
                    if (isEliteTier) {
                        put("double_boost_until", JsonPrimitive(newExpiry))
                    }
                }
                SupabaseClient.main.from("profiles").update(profileJson) { filter { eq("id", uid) } }

                // 3. Insert transaction
                val txJson = buildJsonObject {
                    put("user_id", JsonPrimitive(uid))
                    put("amount", JsonPrimitive(-cost))
                    put("type", JsonPrimitive("shop_purchase"))
                    put("description", JsonPrimitive("Purchased $title for $days days"))
                }
                try { SupabaseClient.main.from("coin_transactions").insert(txJson) } catch (_: Exception) {}

                // 4. Insert Notification
                val notifJson = buildJsonObject {
                    put("user_id", JsonPrimitive(uid))
                    put("title", JsonPrimitive("👑 $title Activated!"))
                    put("message", JsonPrimitive(
                        if (isEliteTier) "Welcome to ELITE PRO! Diamond Aura, 100% Ad-Free, 1080p Ultra HD & 2x Coins unlocked for $days days."
                        else "Welcome to VIP! Gold Profile Badge, 100% Ad-Free & Ultra HD Servers unlocked for $days days."
                    ))
                    put("type", JsonPrimitive("reward"))
                }
                try { SupabaseClient.main.from("notifications").insert(notifJson) } catch (_: Exception) {}

                // 5. Sync AdManager & local boost storage immediately
                if (isEliteTier) {
                    val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                    setBoostData(context, newExpiryInstant.toEpochMilli(), today)
                }
                AdManager.syncProfilePerks(
                    context = context,
                    vipBadge = finalTier,
                    vipBadgeExpires = newExpiry,
                    adFreeUntil = newAdFreeExpiry,
                    doubleBoostUntilStr = if (isEliteTier) newExpiry else currentProfile?.doubleBoostUntil
                )

                _state.value = _state.value.copy(
                    isPurchasing = false,
                    balance = CoinBalance(uid, currentBal - cost, currentEarned, currentSpent + cost),
                    userProfile = currentProfile?.copy(
                        vipBadge = finalTier,
                        vipBadgeExpires = newExpiry,
                        adFreeUntil = newAdFreeExpiry,
                        doubleBoostUntil = if (isEliteTier) newExpiry else currentProfile.doubleBoostUntil
                    ),
                    showPurchaseDialog = true,
                    dialogTitle = if (isEliteTier) "💎 ELITE PRO Unlocked!" else "👑 VIP Badge Unlocked!",
                    isErrorDialog = false,
                    purchaseSuccessMessage = if (isEliteTier) {
                        "ELITE PRO is now active for $days days!\n\n✦ Diamond Rotating Avatar Ring & 8 Stars\n✦ 2x Double Coins on all rewards\n✦ 100% Ad-Free Streaming & Downloads\n✦ 1080p Ultra HD & Priority Requests"
                    } else {
                        "VIP Badge is now active for $days days!\n\n✦ Gold Rotating Avatar Ring & VIP Pill\n✦ 100% Ad-Free Streaming & Downloads\n✦ Ultra HD Servers & Priority Requests"
                    },
                    adFreeExpiry = newAdFreeExpiry
                )

                loadData(context)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isPurchasing = false)
                Toast.makeText(context, com.nexiplay.app.ui.util.UserFacingError.from(e, "Purchase failed. Please try again."), Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun buyPriorityRequest(cost: Int = 30, context: Context, onReadyToNavigate: () -> Unit) {
        if (_state.value.isPurchasing) return
        // VIP & Elite Pro users already get Priority Content Requests for free without ads!
        if (AdManager.isUserPremium()) {
            val shopPrefs = context.getSharedPreferences("NexiPlayShop", Context.MODE_PRIVATE)
            shopPrefs.edit().putBoolean("priority_request_ready", true).apply()
            Toast.makeText(context, "👑 VIP/Elite Perk: Priority Request unlocked for FREE!", Toast.LENGTH_SHORT).show()
            onReadyToNavigate()
            return
        }

        val shopPrefs = context.getSharedPreferences("NexiPlayShop", Context.MODE_PRIVATE)
        if (shopPrefs.getBoolean("priority_request_ready", false)) {
            onReadyToNavigate()
            return
        }

        viewModelScope.launch {
            try { SupabaseClient.main.auth.awaitInitialization() } catch (_: Exception) {}
            val uid = _state.value.userId ?: SupabaseClient.main.auth.currentUserOrNull()?.id ?: return@launch
            _state.value = _state.value.copy(isPurchasing = true)

            try {
                val freshBal = try {
                    SupabaseClient.main.from("coin_balances")
                        .select { filter { eq("user_id", uid) } }
                        .decodeSingleOrNull<CoinBalance>()
                } catch (_: Exception) { null }

                val currentBal = freshBal?.balance ?: _state.value.balance?.balance ?: 0
                val currentEarned = freshBal?.totalEarned ?: _state.value.balance?.totalEarned ?: 0
                val currentSpent = freshBal?.totalSpent ?: _state.value.balance?.totalSpent ?: 0

                if (currentBal < cost) {
                    showInsufficientCoinsDialog(currentBal, cost, "Priority Request", context)
                    return@launch
                }

                val balanceJson = buildJsonObject {
                    put("balance", JsonPrimitive(currentBal - cost))
                    put("total_earned", JsonPrimitive(currentEarned))
                    put("total_spent", JsonPrimitive(currentSpent + cost))
                }
                SupabaseClient.main.from("coin_balances").update(balanceJson) { filter { eq("user_id", uid) } }

                val txJson = buildJsonObject {
                    put("user_id", JsonPrimitive(uid))
                    put("amount", JsonPrimitive(-cost))
                    put("type", JsonPrimitive("shop_purchase"))
                    put("description", JsonPrimitive("Purchased Priority Content Request"))
                }
                try { SupabaseClient.main.from("coin_transactions").insert(txJson) } catch (_: Exception) {}

                shopPrefs.edit().putBoolean("priority_request_ready", true).apply()

                _state.value = _state.value.copy(
                    isPurchasing = false,
                    balance = CoinBalance(uid, currentBal - cost, currentEarned, currentSpent + cost)
                )
                Toast.makeText(context, "⚡ Priority Request unlocked! Submit your request now (No Ad required).", Toast.LENGTH_LONG).show()
                onReadyToNavigate()
            } catch (e: Exception) {
                _state.value = _state.value.copy(isPurchasing = false)
                Toast.makeText(context, com.nexiplay.app.ui.util.UserFacingError.from(e, "Purchase failed. Please try again."), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getBoostExpiry(context: Context): Long {
        val instant = AdManager.getDoubleBoostExpiryInstant(context)
        if (instant != null) return instant.toEpochMilli()
        val prefs = context.getSharedPreferences("NexiPlayShop", Context.MODE_PRIVATE)
        return prefs.getLong("boost_expiry", 0)
    }

    private fun getLastBoostPurchase(context: Context): String {
        val prefs = context.getSharedPreferences("NexiPlayShop", Context.MODE_PRIVATE)
        return prefs.getString("last_boost_purchase", "") ?: ""
    }

    private fun setBoostData(context: Context, expiry: Long, purchaseDate: String) {
        val prefs = context.getSharedPreferences("NexiPlayShop", Context.MODE_PRIVATE)
        prefs.edit().putLong("boost_expiry", expiry).putString("last_boost_purchase", purchaseDate).apply()
    }

    fun isBoostActive(context: Context): Boolean {
        return AdManager.isDoubleCoinBoostActive(context)
    }

    fun buyDoubleCoinBoost(context: Context) {
        if (_state.value.isPurchasing) return
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        if (getLastBoostPurchase(context) == today && isBoostActive(context)) {
            Toast.makeText(context, "⚡ 2x Coin Boost is already active today!", Toast.LENGTH_SHORT).show()
            return
        }

        viewModelScope.launch {
            try { SupabaseClient.main.auth.awaitInitialization() } catch (_: Exception) {}
            val uid = _state.value.userId ?: SupabaseClient.main.auth.currentUserOrNull()?.id ?: return@launch
            val cost = 100
            _state.value = _state.value.copy(isPurchasing = true)

            try {
                val freshBal = try {
                    SupabaseClient.main.from("coin_balances")
                        .select { filter { eq("user_id", uid) } }
                        .decodeSingleOrNull<CoinBalance>()
                } catch (_: Exception) { null }

                val currentBal = freshBal?.balance ?: _state.value.balance?.balance ?: 0
                val currentEarned = freshBal?.totalEarned ?: _state.value.balance?.totalEarned ?: 0
                val currentSpent = freshBal?.totalSpent ?: _state.value.balance?.totalSpent ?: 0

                if (currentBal < cost) {
                    showInsufficientCoinsDialog(currentBal, cost, "2x Coin Boost (24h)", context)
                    return@launch
                }

                // Deduct coins
                val balanceJson = buildJsonObject {
                    put("balance", JsonPrimitive(currentBal - cost))
                    put("total_earned", JsonPrimitive(currentEarned))
                    put("total_spent", JsonPrimitive(currentSpent + cost))
                }
                SupabaseClient.main.from("coin_balances").update(balanceJson) { filter { eq("user_id", uid) } }

                // Calculate new boost expiry (extend from existing active boost if any)
                val now = Instant.now()
                val existingBoost = AdManager.getDoubleBoostExpiryInstant(context)
                val baseBoost = if (existingBoost != null && existingBoost.isAfter(now)) existingBoost else now
                val newBoostInstant = baseBoost.plus(java.time.Duration.ofHours(24))
                val newBoostIso = newBoostInstant.toString()

                // Persist to profiles.double_boost_until in Supabase
                try {
                    val profileJson = buildJsonObject {
                        put("double_boost_until", JsonPrimitive(newBoostIso))
                    }
                    SupabaseClient.main.from("profiles").update(profileJson) { filter { eq("id", uid) } }
                } catch (_: Exception) {}

                // Insert transaction
                val txJson = buildJsonObject {
                    put("user_id", JsonPrimitive(uid))
                    put("amount", JsonPrimitive(-cost))
                    put("type", JsonPrimitive("shop_purchase"))
                    put("description", JsonPrimitive("Purchased 2x Coin Boost for 24h"))
                }
                try { SupabaseClient.main.from("coin_transactions").insert(txJson) } catch (_: Exception) {}

                // Update local storage & AdManager
                setBoostData(context, newBoostInstant.toEpochMilli(), today)
                AdManager.doubleBoostUntil = newBoostIso
                AdManager.setAdFreeExpiry(context, AdManager.getAdFreeExpiry(context))

                _state.value = _state.value.copy(
                    isPurchasing = false,
                    balance = CoinBalance(uid, currentBal - cost, currentEarned, currentSpent + cost),
                    userProfile = _state.value.userProfile?.copy(doubleBoostUntil = newBoostIso),
                    showPurchaseDialog = true,
                    dialogTitle = "⚡ 2x Coin Boost Active!",
                    isErrorDialog = false,
                    purchaseSuccessMessage = "Double Coin Boost is now active for the next 24 hours! You will earn 2x coins on Daily Login, Episodes, Downloads, Comments, and Reward Ads."
                )

                loadData(context)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isPurchasing = false)
                Toast.makeText(context, com.nexiplay.app.ui.util.UserFacingError.from(e, "Purchase failed. Please try again."), Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun dismissPurchaseDialog() {
        _state.value = _state.value.copy(showPurchaseDialog = false, purchaseSuccessMessage = null, isErrorDialog = false)
    }
}
