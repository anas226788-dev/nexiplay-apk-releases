package com.nexiplay.app.data.util

import android.content.Context
import android.widget.Toast
import com.nexiplay.app.data.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Tracks daily episode watches and downloads, awarding coins when thresholds are met.
 *
 * Rules:
 * - Watch 2 episodes in a day → +35 coins (once per day)
 * - Download 2 content in a day → +20 coins (once per day)
 */
object CoinRewardHelper {
    private const val PREFS_NAME = "CoinRewardTracker"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun today(): String = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

    private fun currentUserKey(): String =
        try { SupabaseClient.main.auth.currentUserOrNull()?.id ?: "guest" } catch (_: Exception) { "guest" }

    // ── Public Progress Helpers for CoinCenterScreen ──

    fun getDailyWatchCount(context: Context): Int {
        val p = prefs(context)
        val date = today()
        val uk = currentUserKey()
        val savedDate = p.getString("watch_date_$uk", p.getString("watch_date", "")) ?: ""
        return if (savedDate == date) {
            maxOf(p.getInt("watch_count_$uk", 0), p.getInt("watch_count", 0))
        } else 0
    }

    fun isDailyWatchRewarded(context: Context): Boolean {
        val p = prefs(context)
        val date = today()
        val uk = currentUserKey()
        val savedDate = p.getString("watch_date_$uk", p.getString("watch_date", "")) ?: ""
        return savedDate == date && (p.getBoolean("watch_rewarded_$uk", false) || p.getBoolean("watch_rewarded", false))
    }

    fun getDailyDownloadCount(context: Context): Int {
        val p = prefs(context)
        val date = today()
        val uk = currentUserKey()
        val savedDate = p.getString("download_date_$uk", p.getString("download_date", "")) ?: ""
        return if (savedDate == date) {
            maxOf(p.getInt("download_count_$uk", 0), p.getInt("download_count", 0))
        } else 0
    }

    fun isDailyDownloadRewarded(context: Context): Boolean {
        val p = prefs(context)
        val date = today()
        val uk = currentUserKey()
        val savedDate = p.getString("download_date_$uk", p.getString("download_date", "")) ?: ""
        return savedDate == date && (p.getBoolean("download_rewarded_$uk", false) || p.getBoolean("download_rewarded", false))
    }

    fun markDailyRewardedFromServer(context: Context, uid: String, type: String) {
        val p = prefs(context)
        val date = today()
        when (type) {
            "daily_watch" -> {
                p.edit()
                    .putString("watch_date_$uid", date)
                    .putInt("watch_count_$uid", maxOf(2, getDailyWatchCount(context)))
                    .putBoolean("watch_rewarded_$uid", true)
                    .apply()
            }
            "daily_download" -> {
                p.edit()
                    .putString("download_date_$uid", date)
                    .putInt("download_count_$uid", maxOf(2, getDailyDownloadCount(context)))
                    .putBoolean("download_rewarded_$uid", true)
                    .apply()
            }
        }
    }

    // ── Episode Watch Tracking ──

    fun recordEpisodeWatch(context: Context) {
        val appContext = context.applicationContext
        val p = prefs(appContext)
        val date = today()
        val uk = currentUserKey()

        val savedDate = p.getString("watch_date_$uk", "") ?: ""
        val count = if (savedDate == date) p.getInt("watch_count_$uk", 0) else 0
        val alreadyRewarded = savedDate == date && p.getBoolean("watch_rewarded_$uk", false)

        val newCount = count + 1
        p.edit()
            .putString("watch_date_$uk", date)
            .putInt("watch_count_$uk", newCount)
            .putBoolean("watch_rewarded_$uk", alreadyRewarded)
            .putString("watch_date", date)
            .putInt("watch_count", newCount)
            .apply()

        // Award coins when reaching 2 watches (only once per day)
        if (newCount >= 2 && !alreadyRewarded) {
            awardCoins(
                context = appContext,
                amount = 35,
                type = "daily_watch",
                description = "Watched 2 episodes today 🎬",
                onSuccess = { uid ->
                    p.edit()
                        .putString("watch_date_$uid", date)
                        .putBoolean("watch_rewarded_$uid", true)
                        .putBoolean("watch_rewarded", true)
                        .apply()
                }
            )
        }
    }

    // ── Download Tracking ──

    fun recordDownload(context: Context) {
        val appContext = context.applicationContext
        val p = prefs(appContext)
        val date = today()
        val uk = currentUserKey()

        val savedDate = p.getString("download_date_$uk", "") ?: ""
        val count = if (savedDate == date) p.getInt("download_count_$uk", 0) else 0
        val alreadyRewarded = savedDate == date && p.getBoolean("download_rewarded_$uk", false)

        val newCount = count + 1
        p.edit()
            .putString("download_date_$uk", date)
            .putInt("download_count_$uk", newCount)
            .putBoolean("download_rewarded_$uk", alreadyRewarded)
            .putString("download_date", date)
            .putInt("download_count", newCount)
            .apply()

        // Award coins when reaching 2 downloads (only once per day)
        if (newCount >= 2 && !alreadyRewarded) {
            awardCoins(
                context = appContext,
                amount = 20,
                type = "daily_download",
                description = "Downloaded 2 content today 📥",
                onSuccess = { uid ->
                    p.edit()
                        .putString("download_date_$uid", date)
                        .putBoolean("download_rewarded_$uid", true)
                        .putBoolean("download_rewarded", true)
                        .apply()
                }
            )
        }
    }

    // ── Shared coin awarding ──

    private fun awardCoins(
        context: Context,
        amount: Int,
        type: String,
        description: String,
        onSuccess: (String) -> Unit = {}
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                try { SupabaseClient.main.auth.awaitInitialization() } catch (_: Exception) {}
                val user = SupabaseClient.main.auth.currentUserOrNull() ?: return@launch
                val uid = user.id

                // Check boost (Elite Pro 30d boost, DB double_boost_until, or 24h shop boost)
                val isBoosted = AdManager.isDoubleCoinBoostActive(context)
                val finalAmount = if (isBoosted) amount * 2 else amount

                // 1. Update or insert balance
                val balance = try {
                    SupabaseClient.main.from("coin_balances")
                        .select { filter { eq("user_id", uid) } }
                        .decodeSingleOrNull<com.nexiplay.app.data.model.CoinBalance>()
                } catch (_: Exception) { null }

                if (balance == null) {
                    val insertJson = buildJsonObject {
                        put("user_id", JsonPrimitive(uid))
                        put("balance", JsonPrimitive(finalAmount))
                        put("total_earned", JsonPrimitive(finalAmount))
                        put("total_spent", JsonPrimitive(0))
                    }
                    SupabaseClient.main.from("coin_balances").insert(insertJson)
                } else {
                    val balanceJson = buildJsonObject {
                        put("balance", JsonPrimitive(balance.balance + finalAmount))
                        put("total_earned", JsonPrimitive(balance.totalEarned + finalAmount))
                        put("total_spent", JsonPrimitive(balance.totalSpent))
                    }
                    SupabaseClient.main.from("coin_balances").update(balanceJson) {
                        filter { eq("user_id", uid) }
                    }
                }

                // Mark rewarded ONLY after balance update succeeds
                onSuccess(uid)

                // 2. Insert transaction
                val txJson = buildJsonObject {
                    put("user_id", JsonPrimitive(uid))
                    put("amount", JsonPrimitive(finalAmount))
                    put("type", JsonPrimitive(type))
                    put("description", JsonPrimitive(
                        if (isBoosted) "$description (2x Boost!)" else description
                    ))
                }
                try { SupabaseClient.main.from("coin_transactions").insert(txJson) } catch (_: Exception) {}

                // 3. Insert notification
                val notifJson = buildJsonObject {
                    put("user_id", JsonPrimitive(uid))
                    put("title", JsonPrimitive("Coins Earned! 🪙"))
                    put("message", JsonPrimitive("You earned $finalAmount coins! $description"))
                    put("type", JsonPrimitive("reward"))
                }
                try { SupabaseClient.main.from("notifications").insert(notifJson) } catch (_: Exception) {}

                // Toast on main thread
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(context, "🪙 +$finalAmount coins! $description", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("CoinRewardHelper", "Failed to award coins: ${e.message}")
            }
        }
    }
}
