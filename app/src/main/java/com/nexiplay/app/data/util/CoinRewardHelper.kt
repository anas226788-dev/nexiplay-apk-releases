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

    // ── Episode Watch Tracking ──

    fun recordEpisodeWatch(context: Context) {
        val p = prefs(context)
        val date = today()

        // Reset counter if it's a new day
        val savedDate = p.getString("watch_date", "") ?: ""
        val count = if (savedDate == date) p.getInt("watch_count", 0) else 0
        val alreadyRewarded = savedDate == date && p.getBoolean("watch_rewarded", false)

        val newCount = count + 1
        p.edit()
            .putString("watch_date", date)
            .putInt("watch_count", newCount)
            .apply()

        // Award coins when reaching 2 watches (only once per day)
        if (newCount >= 2 && !alreadyRewarded) {
            p.edit().putBoolean("watch_rewarded", true).apply()
            awardCoins(context, 35, "daily_watch", "Watched 2 episodes today 🎬")
        }
    }

    // ── Download Tracking ──

    fun recordDownload(context: Context) {
        val p = prefs(context)
        val date = today()

        val savedDate = p.getString("download_date", "") ?: ""
        val count = if (savedDate == date) p.getInt("download_count", 0) else 0
        val alreadyRewarded = savedDate == date && p.getBoolean("download_rewarded", false)

        val newCount = count + 1
        p.edit()
            .putString("download_date", date)
            .putInt("download_count", newCount)
            .apply()

        // Award coins when reaching 2 downloads (only once per day)
        if (newCount >= 2 && !alreadyRewarded) {
            p.edit().putBoolean("download_rewarded", true).apply()
            awardCoins(context, 20, "daily_download", "Downloaded 2 content today 📥")
        }
    }

    // ── Shared coin awarding ──

    private fun awardCoins(context: Context, amount: Int, type: String, description: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val user = SupabaseClient.main.auth.currentUserOrNull() ?: return@launch
                val uid = user.id

                // Check boost
                val shopPrefs = context.getSharedPreferences("NexiPlayShop", Context.MODE_PRIVATE)
                val boostExpiry = shopPrefs.getLong("boost_expiry", 0)
                val finalAmount = if (System.currentTimeMillis() < boostExpiry) amount * 2 else amount

                // 1. Update balance
                val balance = try {
                    SupabaseClient.main.from("coin_balances")
                        .select { filter { eq("user_id", uid) } }
                        .decodeSingle<com.nexiplay.app.data.model.CoinBalance>()
                } catch (_: Exception) { return@launch }

                val balanceJson = buildJsonObject {
                    put("balance", JsonPrimitive(balance.balance + finalAmount))
                    put("total_earned", JsonPrimitive(balance.totalEarned + finalAmount))
                    put("total_spent", JsonPrimitive(balance.totalSpent))
                }
                SupabaseClient.main.from("coin_balances").update(balanceJson) {
                    filter { eq("user_id", uid) }
                }

                // 2. Insert transaction
                val txJson = buildJsonObject {
                    put("user_id", JsonPrimitive(uid))
                    put("amount", JsonPrimitive(finalAmount))
                    put("type", JsonPrimitive(type))
                    put("description", JsonPrimitive(
                        if (System.currentTimeMillis() < boostExpiry) "$description (2x Boost!)" else description
                    ))
                }
                SupabaseClient.main.from("coin_transactions").insert(txJson)

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
