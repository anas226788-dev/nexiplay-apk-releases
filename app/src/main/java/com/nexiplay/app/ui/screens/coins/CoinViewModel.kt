package com.nexiplay.app.ui.screens.coins

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexiplay.app.data.SupabaseClient
import com.nexiplay.app.data.model.CoinBalance
import com.nexiplay.app.data.model.CoinStreak
import com.nexiplay.app.data.model.CoinTransaction
import com.nexiplay.app.data.model.UserProfile
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

data class CoinState(
    val balance: CoinBalance? = null,
    val streak: CoinStreak? = null,
    val userProfile: UserProfile? = null,
    val userId: String? = null,
    val isLoggedIn: Boolean = false,
    val isLoading: Boolean = true,
    val claimStatus: ClaimStatus = ClaimStatus.CHECKING,
    val claimMessage: String? = null,
    val lastReward: Int = 0,
    val profileError: String? = null,
    val showPurchaseDialog: Boolean = false,
    val purchaseSuccessMessage: String? = null,
    val adFreeExpiry: String? = null,
)

enum class ClaimStatus {
    CHECKING,      // Initial - checking DB
    AVAILABLE,     // Can claim today
    CLAIMING,      // In progress
    CLAIMED_NOW,   // Just claimed this session
    ALREADY_CLAIMED, // Already claimed today (from DB)
    ERROR,
}

class CoinViewModel : ViewModel() {
    private val _state = MutableStateFlow(CoinState())
    val state = _state.asStateFlow()

    init { initialize() }

    private fun initialize() {
        viewModelScope.launch {
            val user = SupabaseClient.main.auth.currentUserOrNull()
            if (user == null) {
                _state.value = CoinState(isLoggedIn = false, isLoading = false, claimStatus = ClaimStatus.AVAILABLE)
                return@launch
            }

            _state.value = _state.value.copy(userId = user.id, isLoggedIn = true)
            loadData()
        }
    }

    fun loadData() {
        viewModelScope.launch {
            val uid = _state.value.userId ?: return@launch
            _state.value = _state.value.copy(isLoading = true)

            try {
                // Fetch balance
                val balance = try {
                    SupabaseClient.main.from("coin_balances")
                        .select { filter { eq("user_id", uid) } }
                        .decodeSingleOrNull<CoinBalance>()
                } catch (_: Exception) { null }

                // Fetch streak
                val streak = try {
                    SupabaseClient.main.from("coin_streaks")
                        .select { filter { eq("user_id", uid) } }
                        .decodeSingleOrNull<CoinStreak>()
                } catch (_: Exception) { null }

                // Fetch User Profile
                var profile: UserProfile? = null
                var profileErrorMsg: String? = null
                try {
                    profile = SupabaseClient.main.from("profiles")
                        .select { filter { eq("id", uid) } }
                        .decodeSingleOrNull<UserProfile>()
                        
                    // If profile doesn't exist for some reason, create it
                    if (profile == null) {
                        val newCode = java.util.UUID.randomUUID().toString().substring(0, 8).uppercase()
                        val newProfileJson = buildJsonObject {
                            put("id", JsonPrimitive(uid))
                            put("referral_code", JsonPrimitive(newCode))
                        }
                        try {
                            SupabaseClient.main.from("profiles").insert(newProfileJson)
                            profile = SupabaseClient.main.from("profiles")
                                .select { filter { eq("id", uid) } }
                                .decodeSingleOrNull<UserProfile>()
                        } catch (e: Exception) {
                            profileErrorMsg = "Failed to insert profile: ${e.message}"
                            android.util.Log.e("CoinViewModel", profileErrorMsg!!)
                        }
                    } else if (profile.referralCode == null) {
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
                            profileErrorMsg = "Failed to update referral code: ${e.message}"
                            android.util.Log.e("CoinViewModel", profileErrorMsg!!)
                        }
                    }
                } catch (e: Exception) { 
                    profileErrorMsg = "Failed to fetch profile: ${e.message}"
                    android.util.Log.e("CoinViewModel", "Failed to fetch profile: ${e.stackTraceToString()}")
                }

                // Determine if already claimed today
                val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                val lastClaim = streak?.lastDailyClaim ?: ""
                val alreadyClaimed = lastClaim.startsWith(today)

                _state.value = _state.value.copy(
                    balance = balance,
                    streak = streak,
                    userProfile = profile,
                    profileError = profileErrorMsg,
                    isLoading = false,
                    claimStatus = if (alreadyClaimed) ClaimStatus.ALREADY_CLAIMED else ClaimStatus.AVAILABLE,
                    adFreeExpiry = profile?.adFreeUntil
                )

            } catch (e: Exception) {
                // Ensure we don't get stuck in loading state
                _state.value = _state.value.copy(
                    isLoading = false,
                    claimStatus = ClaimStatus.ERROR,
                    claimMessage = e.message
                )
                android.util.Log.e("CoinViewModel", "Critical error in loadData: ${e.stackTraceToString()}")
            }
        }
    }

    fun getNextClaimReward(context: android.content.Context): Int {
        val streak = _state.value.streak
        val todayDate = LocalDate.now()
        val lastClaimStr = streak?.lastDailyClaim?.takeIf { it.isNotBlank() }
            ?: streak?.lastLoginDate?.takeIf { it.isNotBlank() }
            ?: ""
        val oldStreak = streak?.currentStreak ?: 0

        val lastClaimDate = try {
            if (lastClaimStr.length >= 10) {
                LocalDate.parse(lastClaimStr.substring(0, 10))
            } else null
        } catch (_: Exception) { null }

        val nextStreak = if (lastClaimDate == null) {
            1
        } else {
            val daysBetween = ChronoUnit.DAYS.between(lastClaimDate, todayDate)
            when (daysBetween) {
                0L -> oldStreak
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

    fun isStreakMilestoneClaimed(milestoneDays: Int, context: android.content.Context): Boolean {
        val uid = _state.value.userId ?: return false
        val prefs = context.getSharedPreferences("NexiPlayMilestones", android.content.Context.MODE_PRIVATE)
        return prefs.getBoolean("claimed_${milestoneDays}_$uid", false)
    }

    fun claimStreakMilestone(milestoneDays: Int, rewardAmount: Int, context: android.content.Context) {
        viewModelScope.launch {
            val uid = _state.value.userId ?: return@launch
            val streak = _state.value.streak
            val curStreak = streak?.currentStreak ?: 0
            val bestStreak = streak?.longestStreak ?: 0
            val reached = curStreak >= milestoneDays || bestStreak >= milestoneDays

            if (!reached) {
                android.widget.Toast.makeText(context, "You need a $milestoneDays-day streak to claim this!", android.widget.Toast.LENGTH_SHORT).show()
                return@launch
            }

            val prefs = context.getSharedPreferences("NexiPlayMilestones", android.content.Context.MODE_PRIVATE)
            val key = "claimed_${milestoneDays}_$uid"
            if (prefs.getBoolean(key, false)) {
                android.widget.Toast.makeText(context, "You have already claimed this milestone reward!", android.widget.Toast.LENGTH_SHORT).show()
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

                val txJson = buildJsonObject {
                    put("user_id", JsonPrimitive(uid))
                    put("amount", JsonPrimitive(finalAmount))
                    put("type", JsonPrimitive("streak_milestone_$milestoneDays"))
                    put("description", JsonPrimitive("🎉 Claimed $milestoneDays-Day Streak Milestone Reward!"))
                }
                SupabaseClient.main.from("coin_transactions").insert(txJson)

                prefs.edit().putBoolean(key, true).apply()

                _state.value = _state.value.copy(
                    balance = CoinBalance(
                        userId = uid,
                        balance = newBal,
                        totalEarned = newEarned,
                        totalSpent = currentSpent
                    ),
                    showPurchaseDialog = true,
                    purchaseSuccessMessage = "🎉 Awesome! You earned +$finalAmount coins for reaching the $milestoneDays-Day Streak Milestone!"
                )

                delay(300)
                loadData()
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Failed to claim milestone: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun claimDaily(context: android.content.Context) {
        viewModelScope.launch {
            val uid = _state.value.userId ?: return@launch
            if (_state.value.claimStatus != ClaimStatus.AVAILABLE) return@launch

            _state.value = _state.value.copy(claimStatus = ClaimStatus.CLAIMING)

            try {
                val todayDate = LocalDate.now()
                val today = todayDate.format(DateTimeFormatter.ISO_LOCAL_DATE)

                // ── Double-check server-side ──
                val freshStreak = try {
                    SupabaseClient.main.from("coin_streaks")
                        .select { filter { eq("user_id", uid) } }
                        .decodeSingleOrNull<CoinStreak>()
                } catch (_: Exception) { null }

                val lastClaimStr = freshStreak?.lastDailyClaim?.takeIf { it.isNotBlank() }
                    ?: freshStreak?.lastLoginDate?.takeIf { it.isNotBlank() }
                    ?: ""
                if (lastClaimStr.startsWith(today)) {
                    // Already claimed today - sync UI
                    _state.value = _state.value.copy(
                        streak = freshStreak,
                        claimStatus = ClaimStatus.ALREADY_CLAIMED,
                    )
                    loadData()
                    return@launch
                }

                // ── Calculate streak ──
                val oldStreak = freshStreak?.currentStreak ?: 0
                val lastClaimDate = try {
                    if (lastClaimStr.length >= 10) {
                        LocalDate.parse(lastClaimStr.substring(0, 10))
                    } else null
                } catch (e: Exception) { null }

                val newStreak = if (lastClaimDate == null) {
                    1
                } else {
                    val daysBetween = ChronoUnit.DAYS.between(lastClaimDate, todayDate)
                    when {
                        daysBetween <= 0L -> maxOf(1, oldStreak) // Same day fallback
                        daysBetween == 1L -> oldStreak + 1      // Claimed yesterday -> increment streak!
                        else -> 1                               // Missed 1 or more days -> reset to 1
                    }
                }
                val longestStreak = maxOf(newStreak, freshStreak?.longestStreak ?: 0)

                // ── Calculate reward ──
                val baseReward = when {
                    newStreak > 0 && newStreak % 30 == 0 -> 250
                    newStreak > 0 && newStreak % 7 == 0 -> 50
                    else -> 5
                }
                
                val reward = if (isBoostActive(context)) baseReward * 2 else baseReward

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
                val desc = when {
                    newStreak > 0 && newStreak % 30 == 0 -> "🎉 30-day streak bonus ($newStreak days)!"
                    newStreak > 0 && newStreak % 7 == 0 -> "🔥 7-day streak bonus ($newStreak days)!"
                    else -> "Daily login reward (Day $newStreak)"
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
                        put("last_daily_claim", JsonPrimitive(today))
                    }
                    SupabaseClient.main.from("coin_streaks").insert(insertStreakJson)
                } else {
                    val streakJson = buildJsonObject {
                        put("current_streak", JsonPrimitive(newStreak))
                        put("longest_streak", JsonPrimitive(longestStreak))
                        put("last_login_date", JsonPrimitive(today))
                        put("last_daily_claim", JsonPrimitive(today))
                    }
                    SupabaseClient.main.from("coin_streaks").update(streakJson) { filter { eq("user_id", uid) } }
                }

                // ── 4. Insert Notification ──
                val notifJson = buildJsonObject {
                    put("user_id", JsonPrimitive(uid))
                    put("title", JsonPrimitive("Daily Reward Claimed!"))
                    put("message", JsonPrimitive("You earned $reward coins. $desc"))
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
                        lastDailyClaim = today,
                    ),
                    claimStatus = ClaimStatus.CLAIMED_NOW,
                    lastReward = reward,
                    claimMessage = desc,
                )

                delay(300)
                loadData()

            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    claimStatus = ClaimStatus.ERROR,
                    claimMessage = e.message ?: "Claim failed",
                )
            }
        }
    }

    fun claimAdReward(amount: Int, context: android.content.Context) {
        viewModelScope.launch {
            val uid = _state.value.userId ?: return@launch
            try {
                val finalAmount = if (isBoostActive(context)) amount * 2 else amount
                // ── 1. Upsert coin_balances ──
                val currentBal = _state.value.balance?.balance ?: 0
                val currentEarned = _state.value.balance?.totalEarned ?: 0
                val currentSpent = _state.value.balance?.totalSpent ?: 0

                val balanceJson = buildJsonObject {
                    put("balance", JsonPrimitive(currentBal + finalAmount))
                    put("total_earned", JsonPrimitive(currentEarned + finalAmount))
                    put("total_spent", JsonPrimitive(currentSpent))
                }
                SupabaseClient.main.from("coin_balances").update(balanceJson) { filter { eq("user_id", uid) } }

                // ── 2. Insert coin_transaction ──
                val txJson = buildJsonObject {
                    put("user_id", JsonPrimitive(uid))
                    put("amount", JsonPrimitive(finalAmount))
                    put("type", JsonPrimitive("reward_ad"))
                    put("description", JsonPrimitive(if (isBoostActive(context)) "Watched a reward ad (2x Boost!)" else "Watched a reward ad"))
                }
                SupabaseClient.main.from("coin_transactions").insert(txJson)

                // ── 3. Update local state immediately ──
                _state.value = _state.value.copy(
                    balance = CoinBalance(
                        userId = uid,
                        balance = currentBal + finalAmount,
                        totalEarned = currentEarned + finalAmount,
                        totalSpent = currentSpent,
                    ),
                    claimStatus = ClaimStatus.CLAIMED_NOW, // Hack to show message
                    lastReward = finalAmount,
                    claimMessage = "You earned $finalAmount coins from watching an ad!",
                )

                delay(500)
                loadData()
            } catch (e: Exception) {
                // Ignore error silently
            }
        }
    }

    suspend fun redeemReferralCode(code: String): Result<String> {
        val uid = _state.value.userId ?: return Result.failure(Exception("Not logged in"))
        
        return try {
            // We call a secure Postgres RPC function that handles RLS and balances automatically
            val postgrest = SupabaseClient.main.pluginManager.getPlugin(io.github.jan.supabase.postgrest.Postgrest)
            val result = postgrest.rpc<kotlinx.serialization.json.JsonObject>(
                function = "redeem_referral",
                parameters = buildJsonObject {
                    put("ref_code", kotlinx.serialization.json.JsonPrimitive(code))
                }
            )
            
            // Reload data
            loadData()
            
            Result.success("Success! You received 5 coins, and your friend received 50 coins.")
        } catch (e: Exception) {
            val errorMsg = e.message ?: "Something went wrong"
            if (errorMsg.contains("invalid_code")) return Result.failure(Exception("Invalid referral code!"))
            if (errorMsg.contains("own_code")) return Result.failure(Exception("You cannot use your own code!"))
            if (errorMsg.contains("already_redeemed")) return Result.failure(Exception("You have already redeemed a code!"))
            Result.failure(Exception("Failed to redeem. $errorMsg"))
        }
    }

    suspend fun submitContactMessage(name: String, email: String, subject: String, message: String): Result<String> {
        return try {
            val postgrest = SupabaseClient.main.pluginManager.getPlugin(io.github.jan.supabase.postgrest.Postgrest)
            postgrest.rpc(
                function = "submit_contact_message",
                parameters = buildJsonObject {
                    put("p_name", kotlinx.serialization.json.JsonPrimitive(name))
                    put("p_email", kotlinx.serialization.json.JsonPrimitive(email))
                    put("p_subject", kotlinx.serialization.json.JsonPrimitive(subject))
                    put("p_message", kotlinx.serialization.json.JsonPrimitive(message))
                }
            )
            
            // Reload data to reflect possible coin increase
            loadData()
            
            Result.success("Message sent successfully!")
        } catch (e: Exception) {
            Result.failure(Exception("Failed: ${e.message}"))
        }
    }

    fun buyAdFree(days: Int, cost: Int, context: android.content.Context) {
        viewModelScope.launch {
            val uid = _state.value.userId ?: return@launch
            val currentBal = _state.value.balance?.balance ?: 0
            if (currentBal < cost) {
                android.widget.Toast.makeText(context, "Not enough coins! You need $cost \uD83E\uDE99", android.widget.Toast.LENGTH_SHORT).show()
                return@launch
            }

            try {
                // 1. Deduct coins from balances
                val currentEarned = _state.value.balance?.totalEarned ?: 0
                val currentSpent = _state.value.balance?.totalSpent ?: 0
                val balanceJson = buildJsonObject {
                    put("balance", JsonPrimitive(currentBal - cost))
                    put("total_earned", JsonPrimitive(currentEarned))
                    put("total_spent", JsonPrimitive(currentSpent + cost))
                }
                SupabaseClient.main.from("coin_balances").update(balanceJson) { filter { eq("user_id", uid) } }

                // 2. Insert transaction
                val txJson = buildJsonObject {
                    put("user_id", JsonPrimitive(uid))
                    put("amount", JsonPrimitive(-cost))
                    put("type", JsonPrimitive("shop_purchase"))
                    put("description", JsonPrimitive("Purchased Ad-Free for $days days"))
                }
                SupabaseClient.main.from("coin_transactions").insert(txJson)

                // 3. Update ad_free_until in profiles
                val newExpiry = java.time.Instant.now().plus(java.time.Duration.ofDays(days.toLong())).toString()
                val profileJson = buildJsonObject {
                    put("ad_free_until", JsonPrimitive(newExpiry))
                }
                SupabaseClient.main.from("profiles").update(profileJson) { filter { eq("id", uid) } }

                // Reload local state
                loadData()
                
                // Also update AdManager cache directly so ads stop immediately
                com.nexiplay.app.data.util.AdManager.setAdFreeExpiry(context, newExpiry)
                
                _state.value = _state.value.copy(
                    showPurchaseDialog = true,
                    purchaseSuccessMessage = "Ad-Free unlocked for $days days! Enjoy NexiPlay without any interruptions.",
                    adFreeExpiry = newExpiry
                )
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Purchase failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun buyVipBadge(tier: String, days: Int, cost: Int, context: android.content.Context) {
        viewModelScope.launch {
            val uid = _state.value.userId ?: return@launch
            val currentBal = _state.value.balance?.balance ?: 0
            if (currentBal < cost) {
                android.widget.Toast.makeText(context, "Not enough coins! You need $cost \uD83E\uDE99", android.widget.Toast.LENGTH_SHORT).show()
                return@launch
            }

            try {
                // 1. Deduct coins from balances
                val currentEarned = _state.value.balance?.totalEarned ?: 0
                val currentSpent = _state.value.balance?.totalSpent ?: 0
                val balanceJson = buildJsonObject {
                    put("balance", JsonPrimitive(currentBal - cost))
                    put("total_earned", JsonPrimitive(currentEarned))
                    put("total_spent", JsonPrimitive(currentSpent + cost))
                }
                SupabaseClient.main.from("coin_balances").update(balanceJson) { filter { eq("user_id", uid) } }

                // 2. Insert transaction
                val txJson = buildJsonObject {
                    put("user_id", JsonPrimitive(uid))
                    put("amount", JsonPrimitive(-cost))
                    put("type", JsonPrimitive("shop_purchase"))
                    put("description", JsonPrimitive("Purchased $tier Badge for $days days"))
                }
                SupabaseClient.main.from("coin_transactions").insert(txJson)

                // 3. Update vip_badge, vip_badge_expires, and ad_free_until in profiles
                val newExpiry = java.time.Instant.now().plus(java.time.Duration.ofDays(days.toLong())).toString()
                val profileJson = buildJsonObject {
                    put("vip_badge", JsonPrimitive(tier))
                    put("vip_badge_expires", JsonPrimitive(newExpiry))
                    put("ad_free_until", JsonPrimitive(newExpiry))
                }
                SupabaseClient.main.from("profiles").update(profileJson) { filter { eq("id", uid) } }
                
                com.nexiplay.app.data.util.AdManager.userBadgeType = tier
                com.nexiplay.app.data.util.AdManager.setAdFreeExpiry(context, newExpiry)

                // Reload local state
                loadData()
                
                val title = if (tier == "elite_pro") "Elite Pro Badge" else "VIP Badge"
                _state.value = _state.value.copy(
                    showPurchaseDialog = true,
                    purchaseSuccessMessage = "$title unlocked for $days days! Your profile is now glowing!"
                )
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Purchase failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getBoostExpiry(context: android.content.Context): Long {
        val prefs = context.getSharedPreferences("NexiPlayShop", android.content.Context.MODE_PRIVATE)
        return prefs.getLong("boost_expiry", 0)
    }

    private fun getLastBoostPurchase(context: android.content.Context): String {
        val prefs = context.getSharedPreferences("NexiPlayShop", android.content.Context.MODE_PRIVATE)
        return prefs.getString("last_boost_purchase", "") ?: ""
    }

    private fun setBoostData(context: android.content.Context, expiry: Long, purchaseDate: String) {
        val prefs = context.getSharedPreferences("NexiPlayShop", android.content.Context.MODE_PRIVATE)
        prefs.edit().putLong("boost_expiry", expiry).putString("last_boost_purchase", purchaseDate).apply()
    }
    
    fun isBoostActive(context: android.content.Context): Boolean {
        return System.currentTimeMillis() < getBoostExpiry(context)
    }

    fun buyDoubleCoinBoost(context: android.content.Context) {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        if (getLastBoostPurchase(context) == today) {
            android.widget.Toast.makeText(context, "You can only buy this once per day!", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        
        viewModelScope.launch {
            val uid = _state.value.userId ?: return@launch
            val cost = 100
            val currentBal = _state.value.balance?.balance ?: 0
            if (currentBal < cost) {
                android.widget.Toast.makeText(context, "Not enough coins! You need $cost \uD83E\uDE99", android.widget.Toast.LENGTH_SHORT).show()
                return@launch
            }

            try {
                // Deduct coins
                val currentEarned = _state.value.balance?.totalEarned ?: 0
                val currentSpent = _state.value.balance?.totalSpent ?: 0
                val balanceJson = buildJsonObject {
                    put("balance", JsonPrimitive(currentBal - cost))
                    put("total_earned", JsonPrimitive(currentEarned))
                    put("total_spent", JsonPrimitive(currentSpent + cost))
                }
                SupabaseClient.main.from("coin_balances").update(balanceJson) { filter { eq("user_id", uid) } }

                // Insert transaction
                val txJson = buildJsonObject {
                    put("user_id", JsonPrimitive(uid))
                    put("amount", JsonPrimitive(-cost))
                    put("type", JsonPrimitive("shop_purchase"))
                    put("description", JsonPrimitive("Purchased 2x Coin Boost for 24h"))
                }
                SupabaseClient.main.from("coin_transactions").insert(txJson)

                // Update local storage
                val expiry = System.currentTimeMillis() + (24L * 60 * 60 * 1000)
                setBoostData(context, expiry, today)

                // Reload local state
                loadData()
                
                _state.value = _state.value.copy(
                    showPurchaseDialog = true,
                    purchaseSuccessMessage = "Double Coin Boost activated! You will earn 2x coins for the next 24 hours."
                )
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Purchase failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun dismissPurchaseDialog() {
        _state.value = _state.value.copy(showPurchaseDialog = false, purchaseSuccessMessage = null)
    }
}
