package com.nexiplay.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.nexiplay.app.data.SupabaseClient
import com.nexiplay.app.ui.navigation.NexiPlayNavHost
import com.nexiplay.app.ui.theme.NexiPlayTheme
import com.onesignal.OneSignal
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.nexiplay.app.utils.SessionTracker
import com.nexiplay.app.data.model.UserProfile
import io.github.jan.supabase.postgrest.query.Columns
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        com.nexiplay.app.data.util.AdManager.syncAdFreeStatus(this)
        
        // Register this activity for Unity LevelPlay initialization
        // (will init immediately if config is loaded, or defer until config loads)
        com.nexiplay.app.data.util.AdManager.registerActivity(this)
        
        SessionTracker.startSession()
        
        // Link OneSignal user & Request Permission + Sync VIP/Elite ad-free status
        CoroutineScope(Dispatchers.IO).launch {
            OneSignal.Notifications.requestPermission(true)
            
            val uid = SupabaseClient.main.auth.currentUserOrNull()?.id
            if (uid != null) {
                OneSignal.login(uid)
                
                try {
                    val p = SupabaseClient.main.from("profiles")
                        .select(Columns.list("id", "ad_free_until", "vip_badge", "vip_badge_expires")) { filter { eq("id", uid) } }
                        .decodeSingleOrNull<UserProfile>()
                    
                    if (p != null) {
                        // Pick the latest expiry between ad_free_until and vip_badge_expires
                        val adFreeInstant = com.nexiplay.app.data.util.AdManager.parseDateRobust(p.adFreeUntil)
                        val badgeInstant = com.nexiplay.app.data.util.AdManager.parseDateRobust(p.vipBadgeExpires)
                        
                        val latestExpiry = when {
                            adFreeInstant != null && badgeInstant != null -> {
                                if (adFreeInstant.isAfter(badgeInstant)) p.adFreeUntil else p.vipBadgeExpires
                            }
                            adFreeInstant != null -> p.adFreeUntil
                            badgeInstant != null -> p.vipBadgeExpires
                            else -> null
                        }
                        
                        // Store badge type and expiry BEFORE calling setAdFreeExpiry
                        com.nexiplay.app.data.util.AdManager.userBadgeType = p.vipBadge?.trim().orEmpty()
                        com.nexiplay.app.data.util.AdManager.badgeExpiresAt = p.vipBadgeExpires
                        
                        com.nexiplay.app.data.util.AdManager.setAdFreeExpiry(this@MainActivity, latestExpiry)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        
        setContent {
            NexiPlayTheme {
                NexiPlayNavHost()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        try { com.ironsource.mediationsdk.IronSource.onResume(this) } catch (_: Exception) {}
    }

    override fun onPause() {
        super.onPause()
        try { com.ironsource.mediationsdk.IronSource.onPause(this) } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        SessionTracker.stopSession()
    }
}
