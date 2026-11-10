package com.nexiplay.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.nexiplay.app.data.SupabaseClient
import com.nexiplay.app.data.util.EpisodeUpdatesNotificationManager
import com.nexiplay.app.ui.navigation.NexiPlayNavHost
import com.nexiplay.app.ui.navigation.NotificationRouter
import com.nexiplay.app.ui.theme.NexiPlayTheme
import com.onesignal.OneSignal
import com.onesignal.notifications.INotificationClickEvent
import com.onesignal.notifications.INotificationClickListener
import com.onesignal.notifications.INotificationLifecycleListener
import com.onesignal.notifications.INotificationWillDisplayEvent
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

        EpisodeUpdatesNotificationManager.initChannel(this)
        handleIntentData(intent)
        EpisodeUpdatesNotificationManager.startLiveWatcher(this)

        try {
            OneSignal.Notifications.addForegroundLifecycleListener(object : INotificationLifecycleListener {
                override fun onWillDisplay(event: INotificationWillDisplayEvent) {
                    event.notification.display()
                }
            })
            OneSignal.Notifications.addClickListener(object : INotificationClickListener {
                override fun onClick(event: INotificationClickEvent) {
                    val data = event.notification.additionalData
                    val sNum = data?.optInt("season_number", -1)?.takeIf { it > 0 }
                        ?: data?.optInt("season", -1)?.takeIf { it > 0 }
                        ?: data?.optString("season_number")?.toIntOrNull()
                        ?: data?.optString("season")?.toIntOrNull()
                    val eNum = data?.optInt("episode_number", -1)?.takeIf { it > 0 }
                        ?: data?.optInt("episode", -1)?.takeIf { it > 0 }
                        ?: data?.optString("episode_number")?.toIntOrNull()
                        ?: data?.optString("episode")?.toIntOrNull()

                    NotificationRouter.open(
                        link = data?.optString("deep_link")?.takeIf { !it.isNullOrBlank() && it != "null" }
                            ?: data?.optString("url")?.takeIf { !it.isNullOrBlank() && it != "null" }
                            ?: event.notification.launchURL,
                        screen = data?.optString("screen")?.takeIf { !it.isNullOrBlank() && it != "null" },
                        contentType = data?.optString("content_type")?.takeIf { !it.isNullOrBlank() && it != "null" },
                        slug = data?.optString("slug")?.takeIf { !it.isNullOrBlank() && it != "null" },
                        seasonNumber = sNum,
                        episodeNumber = eNum,
                    )
                }
            })
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        
        com.nexiplay.app.data.util.AdManager.syncAdFreeStatus(this)
        
        // Register this activity for Unity LevelPlay initialization
        com.nexiplay.app.data.util.AdManager.registerActivity(this)
        
        SessionTracker.startSession()
        
        // Link OneSignal user & Request Permission + Sync VIP/Elite ad-free status
        CoroutineScope(Dispatchers.IO).launch {
            OneSignal.Notifications.requestPermission(true)
            try {
                SupabaseClient.main.auth.awaitInitialization()
            } catch (_: Exception) {}
            
            val uid = SupabaseClient.main.auth.currentUserOrNull()?.id
            if (uid != null) {
                OneSignal.login(uid)
                
                try {
                    val p = SupabaseClient.main.from("profiles")
                        .select(Columns.list("id", "ad_free_until", "vip_badge", "vip_badge_expires", "double_boost_until")) { filter { eq("id", uid) } }
                        .decodeSingleOrNull<UserProfile>()
                    
                    if (p != null) {
                        com.nexiplay.app.data.util.AdManager.syncProfilePerks(
                            context = this@MainActivity,
                            vipBadge = p.vipBadge,
                            vipBadgeExpires = p.vipBadgeExpires,
                            adFreeUntil = p.adFreeUntil,
                            doubleBoostUntilStr = p.doubleBoostUntil
                        )
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

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntentData(intent)
    }

    private fun handleIntentData(intent: android.content.Intent?) {
        if (intent == null) return
        val link = intent.dataString
        val extras = intent.extras
        val resolvedLink = link
            ?: extras?.getString("deep_link")?.takeIf { it.isNotBlank() && it != "null" }
            ?: extras?.getString("url")?.takeIf { it.isNotBlank() && it != "null" }
        val screen = extras?.getString("screen")?.takeIf { it.isNotBlank() && it != "null" }
        val contentType = extras?.getString("content_type")?.takeIf { it.isNotBlank() && it != "null" }
        val slug = extras?.getString("slug")?.takeIf { it.isNotBlank() && it != "null" }

        if (resolvedLink == null && screen == null && slug == null) return

        val sNum = extras?.getInt("season_number", -1)?.takeIf { it > 0 }
            ?: extras?.getInt("season", -1)?.takeIf { it > 0 }
            ?: extras?.getString("season_number")?.toIntOrNull()
            ?: extras?.getString("season")?.toIntOrNull()
        val eNum = extras?.getInt("episode_number", -1)?.takeIf { it > 0 }
            ?: extras?.getInt("episode", -1)?.takeIf { it > 0 }
            ?: extras?.getString("episode_number")?.toIntOrNull()
            ?: extras?.getString("episode")?.toIntOrNull()

        NotificationRouter.open(
            link = resolvedLink,
            screen = screen,
            contentType = contentType,
            slug = slug,
            seasonNumber = sNum,
            episodeNumber = eNum
        )

        intent.data = null
        intent.removeExtra("deep_link")
        intent.removeExtra("url")
        intent.removeExtra("screen")
        intent.removeExtra("content_type")
        intent.removeExtra("slug")
    }

    override fun onResume() {
        super.onResume()
        EpisodeUpdatesNotificationManager.checkAndNotifyNewUpdates(this)
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
