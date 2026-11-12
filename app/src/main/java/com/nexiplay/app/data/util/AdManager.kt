package com.nexiplay.app.data.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.nexiplay.app.util.findActivity
import com.startapp.sdk.ads.banner.Banner
import com.startapp.sdk.ads.banner.BannerListener
import com.startapp.sdk.ads.banner.Mrec
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.StartAppSDK
import com.startapp.sdk.adsbase.adlisteners.AdDisplayListener
import com.startapp.sdk.adsbase.adlisteners.AdEventListener
import com.ironsource.mediationsdk.IronSource
import com.ironsource.mediationsdk.ISBannerSize
import com.ironsource.mediationsdk.logger.IronSourceError
import com.ironsource.mediationsdk.sdk.LevelPlayInterstitialListener
import com.ironsource.mediationsdk.sdk.LevelPlayRewardedVideoListener
import com.ironsource.mediationsdk.model.Placement
import com.ironsource.mediationsdk.adunit.adapter.utility.AdInfo
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

enum class AdNetwork { STARTIO, UNITY, BOTH }

@Serializable
data class AdSettings(
    @SerialName("app_ad_network") val adNetwork: String? = null,
    @SerialName("startio_app_id") val startioAppId: String? = null,
    @SerialName("unity_app_key") val unityAppKey: String? = null,
    @SerialName("unity_banner_id") val unityBannerId: String? = null,
    @SerialName("unity_interstitial_id") val unityInterstitialId: String? = null,
    @SerialName("unity_rewarded_id") val unityRewardedId: String? = null,
    @SerialName("is_banner_enabled") val isBannerEnabled: Boolean? = null,
    @SerialName("is_interstitial_enabled") val isInterstitialEnabled: Boolean? = null,
    @SerialName("is_rewarded_enabled") val isRewardedEnabled: Boolean? = null,
    @SerialName("is_native_enabled") val isNativeEnabled: Boolean? = null,
    @SerialName("is_app_open_enabled") val isAppOpenEnabled: Boolean? = null,
    @SerialName("is_premium_server_ad_enabled") val isPremiumServerAdEnabled: Boolean? = null,
    @SerialName("is_test_ads_enabled") val isTestAdsEnabled: Boolean? = null,
    @SerialName("is_ads_enabled") val isAdsEnabled: Boolean? = null,
    @SerialName("direct_link_url") val directLinkUrl: String? = null,
    @SerialName("popunder_url") val popunderUrl: String? = null,
    @SerialName("ad_frequency_session") val adFrequencySession: Int? = null,
    @SerialName("is_download_verification_enabled") val isDownloadVerificationEnabled: Boolean? = null,
    @SerialName("download_ad_url_1") val downloadAdUrl1: String? = null,
    @SerialName("download_ad_url_2") val downloadAdUrl2: String? = null,
    @SerialName("is_verification_enabled") val isVerificationEnabled: Boolean? = null,
    @SerialName("verification_ad_url_1") val verificationAdUrl1: String? = null,
    @SerialName("verification_ad_url_2") val verificationAdUrl2: String? = null,
    @SerialName("coin_popup_enabled") val coinPopupEnabled: Boolean? = true,
    @SerialName("coin_popup_title") val coinPopupTitle: String? = null,
    @SerialName("coin_popup_message") val coinPopupMessage: String? = null,
    @SerialName("coin_popup_button_text") val coinPopupButtonText: String? = null,
    @SerialName("coin_popup_action") val coinPopupAction: String? = null,
    @SerialName("coin_popup_trigger_count") val coinPopupTriggerCount: Int? = 1,
    @SerialName("social_bar_code") val socialBarCode: String? = null,
    @SerialName("app_enabled_servers") val appEnabledServers: String? = null
)

data class DirectLinkOverlaySession(
    val id: Long,
    val url: String,
    val title: String,
    val durationSeconds: Int,
    val actionLabel: String,
    val onCompleted: () -> Unit,
    val onCancelled: () -> Unit = {}
)

object AdManager {
    private val mainHandler = Handler(Looper.getMainLooper())
    private const val PREFS_ADS = "NexiPlayAdsPrefs"
    private const val KEY_LAST_DOWNLOAD_AD_MS = "last_download_direct_link_ms"
    private const val KEY_LAST_STREAM_AD_MS = "last_stream_direct_link_ms"
    private const val KEY_LAST_SPONSOR_TASK_MS = "last_sponsor_task_direct_link_ms"
    private const val KEY_LAST_ANY_DIRECT_LINK_MS = "last_any_direct_link_ms"
    private const val DEFAULT_INTERSTITIAL_COOLDOWN_MS = 180_000L // 3 minutes
    private const val DOWNLOAD_AD_PASS_WINDOW_MS = 8 * 60 * 1000L // 8 min pass after Download Direct Link
    private const val STREAM_AD_PASS_WINDOW_MS = 10 * 60 * 1000L // 10 min pass after Watch Now Direct Link
    private const val MIN_DIRECT_LINK_GAP_MS = 2 * 60 * 1000L // Never stack Watch + Download direct links within 2 min
    private const val SPONSOR_TASK_COOLDOWN_MS = 10 * 60 * 1000L // 10 min cooldown for Coin Center sponsor offer

    var currentNetwork = AdNetwork.STARTIO
    var startioAppId = ""
    var unityAppKey = ""
    var unityBannerId = ""
    var unityInterstitialId = ""
    var unityRewardedId = ""
    var socialBarCode: String? = null
    var appEnabledServers: String? = null

    var isBannerEnabled = true
    var isInterstitialEnabled = true
    var isRewardedEnabled = true
    var isNativeEnabled = false
    var isAppOpenEnabled = false
    var isPremiumServerAdEnabled = true
    var isTestAdsEnabled = true

    // Adsterra Direct Link configuration (synced from app_settings)
    var isDirectLinkAdsEnabled = true
    var directLinkUrl = ""
    var popunderUrl = ""
    var adFrequencySession = 1
    var isDownloadVerificationEnabled = false
    var downloadAdUrl1 = ""
    var downloadAdUrl2 = ""
    var isStreamVerificationEnabled = false
    var verificationAdUrl1 = ""
    var verificationAdUrl2 = ""

    var isUserAdFree = false
    var userBadgeType = ""  // "vip", "elite_pro", "gold_vip", "elite"
    var badgeExpiresAt: String? = null  // ISO date string for badge expiry
    var doubleBoostUntil: String? = null // ISO date string for 2x coin boost expiry

    private var isUnityInitialized = false
    private var isStartIoInitialized = false
    private var lastInterstitialShownAtMs = 0L
    @Volatile var isConfigLoaded = false
    @Volatile private var isStartupReadyForAds = false
    private var pendingActivity: java.lang.ref.WeakReference<Activity>? = null
    private val _premiumState = MutableStateFlow(false)
    val premiumState: StateFlow<Boolean> = _premiumState.asStateFlow()
    private val _configLoadedFlow = MutableStateFlow(false)
    val configLoadedFlow: StateFlow<Boolean> = _configLoadedFlow.asStateFlow()
    private val _directLinkOverlay = MutableStateFlow<DirectLinkOverlaySession?>(null)
    val directLinkOverlay: StateFlow<DirectLinkOverlaySession?> = _directLinkOverlay.asStateFlow()
    private val _badgeUpdateTick = MutableStateFlow(0L)
    val badgeUpdateTick: StateFlow<Long> = _badgeUpdateTick.asStateFlow()

    /**
     * Register activity for deferred ad SDK initialization (executed post-splash so launch is instant).
     */
    fun registerActivity(activity: Activity) {
        pendingActivity = java.lang.ref.WeakReference(activity)
        if (isStartupReadyForAds && isConfigLoaded) {
            initializeConfiguredAdSdks(activity)
        }
    }

    /**
     * Called once the app leaves SplashScreen so Start.io / IronSource never block cold-start frames.
     */
    fun markStartupReadyForAds(activity: Activity) {
        isStartupReadyForAds = true
        pendingActivity = java.lang.ref.WeakReference(activity)
        if (isConfigLoaded) {
            initializeConfiguredAdSdks(activity)
        }
    }

    private fun initializeConfiguredAdSdks(activity: Activity) {
        if (startioAppId.isNotEmpty() && !isStartIoInitialized) {
            initStartIo(activity.applicationContext, startioAppId)
        }
        // Only init IronSource if Unity is active/waterfall and key looks like an IronSource key (not empty)
        if (currentNetwork != AdNetwork.STARTIO && unityAppKey.isNotEmpty() && !isUnityInitialized) {
            initUnityLevelPlay(activity, unityAppKey)
        }
    }

    private fun refreshPremiumState() {
        _premiumState.value = isUserPremium()
        _badgeUpdateTick.value = System.currentTimeMillis()
    }
    
    /** Returns true if user has any active (non-expired) premium badge */
    fun isUserPremium(): Boolean {
        if (isUserAdFree) return true
        val badgeStr = userBadgeType.trim().lowercase()
        if (badgeStr.contains("vip") || badgeStr.contains("elite")) {
            // Check if badge has expired
            val expiry = parseDateRobust(badgeExpiresAt)
            if (expiry != null && expiry.isBefore(java.time.Instant.now())) {
                // Badge expired — clear it
                android.util.Log.d("AdManager", "⏰ Badge '$userBadgeType' expired at $badgeExpiresAt, treating as free user")
                userBadgeType = ""
                badgeExpiresAt = null
                return false
            }
            return true
        }
        return false
    }

    /** Returns true if user has an active (non-expired) Elite Pro / Gold VIP badge */
    fun isUserElite(): Boolean {
        val badgeStr = userBadgeType.trim().lowercase()
        if (badgeStr.contains("elite") || badgeStr == "gold_vip") {
            val expiry = parseDateRobust(badgeExpiresAt)
            if (expiry != null && expiry.isBefore(java.time.Instant.now())) {
                return false
            }
            return true
        }
        return false
    }

    /** Returns the latest active 2x Coin Boost expiry Instant, or null if not active */
    fun getDoubleBoostExpiryInstant(context: Context? = null): java.time.Instant? {
        val now = java.time.Instant.now()
        val candidates = mutableListOf<java.time.Instant>()

        // 1. Elite Pro badge includes 2x Coin Boost for the duration of the badge
        if (isUserElite()) {
            parseDateRobust(badgeExpiresAt)?.takeIf { it.isAfter(now) }?.let { candidates.add(it) }
        }

        // 2. Profile double_boost_until column
        parseDateRobust(doubleBoostUntil)?.takeIf { it.isAfter(now) }?.let { candidates.add(it) }

        // 3. Local NexiPlayShop boost_expiry
        if (context != null) {
            val shopPrefs = context.getSharedPreferences("NexiPlayShop", Context.MODE_PRIVATE)
            val localMs = shopPrefs.getLong("boost_expiry", 0L)
            if (localMs > System.currentTimeMillis()) {
                candidates.add(java.time.Instant.ofEpochMilli(localMs))
            }
        }

        return candidates.maxOrNull()
    }

    /** Returns true if 2x Coin Boost is active (via Elite Pro badge, DB double_boost_until, or local 24h boost) */
    fun isDoubleCoinBoostActive(context: Context? = null): Boolean {
        if (isUserElite() && badgeExpiresAt.isNullOrBlank()) return true
        return getDoubleBoostExpiryInstant(context) != null
    }

    // Marketing Upsell Popup Config
    var coinPopupEnabled = true
    var coinPopupTitle = "Tired of Ads?"
    var coinPopupMessage = "Upgrade to VIP for just 250 Coins to get 30 days of 100% Ad-Free streaming, Gold Profile Badges, & Ultra HD Servers!"
    var coinPopupButtonText = "GET VIP NOW (250 🪙)"
    var coinPopupAction = "buy_vip"
    var coinPopupTriggerCount = 1
    var adDismissCounter = 0
    var onShowUpsellPopup: ((title: String, message: String, btnText: String, action: String) -> Unit)? = null

    fun checkAndTriggerUpsellPopup() {
        if (!isUserPremium() && coinPopupEnabled) {
            adDismissCounter++
            if (adDismissCounter % maxOf(1, coinPopupTriggerCount) == 0) {
                onShowUpsellPopup?.invoke(coinPopupTitle, coinPopupMessage, coinPopupButtonText, coinPopupAction)
            }
        }
    }

    fun parseDateRobust(dateStr: String?): java.time.Instant? {
        if (dateStr.isNullOrEmpty()) return null
        return try {
            var fixed = dateStr.trim().replace(" ", "T")
            if (fixed.endsWith("+00:00")) {
                fixed = fixed.replace("+00:00", "Z")
            } else if (fixed.endsWith("+00")) {
                fixed = fixed.removeSuffix("+00") + "Z"
            }
            if (!fixed.contains("Z") && !fixed.contains("+") && fixed.length > 10) {
                fixed += "Z"
            }
            java.time.Instant.parse(fixed)
        } catch (e: Exception) {
            try {
                java.time.OffsetDateTime.parse(dateStr.trim().replace(" ", "T")).toInstant()
            } catch (_: Exception) {
                null
            }
        }
    }

    fun syncAdFreeStatus(context: Context) {
        val prefs = context.getSharedPreferences("NexiPlayPrefs", Context.MODE_PRIVATE)
        val expiry = prefs.getString("ad_free_until", null)
        
        val savedBadge = prefs.getString("user_badge_type", null)
        userBadgeType = savedBadge?.trim().orEmpty()
        badgeExpiresAt = prefs.getString("badge_expires_at", null)
        doubleBoostUntil = prefs.getString("double_boost_until", null)
        
        val now = java.time.Instant.now()

        // Check if badge has expired and clear it
        val badgeExpiry = parseDateRobust(badgeExpiresAt)
        if (badgeExpiry != null && badgeExpiry.isBefore(now)) {
            android.util.Log.d("AdManager", "⏰ Badge expired, clearing: $userBadgeType")
            userBadgeType = ""
            badgeExpiresAt = null
            prefs.edit().remove("user_badge_type").remove("badge_expires_at").apply()
        }

        // Check if double_boost_until has expired
        val boostExpiry = parseDateRobust(doubleBoostUntil)
        if (boostExpiry != null && boostExpiry.isBefore(now)) {
            doubleBoostUntil = null
            prefs.edit().remove("double_boost_until").apply()
        }
        
        // Check ad_free_until or active badge expiry
        val adFreeParsed = parseDateRobust(expiry)
        val validBadgeExpiry = parseDateRobust(badgeExpiresAt)?.takeIf { it.isAfter(now) }
        val activeAdFree = (adFreeParsed != null && adFreeParsed.isAfter(now)) ||
            (userBadgeType.isNotEmpty() && (validBadgeExpiry != null || badgeExpiresAt.isNullOrBlank()))

        isUserAdFree = activeAdFree
        refreshPremiumState()
    }

    fun syncProfilePerks(
        context: Context,
        vipBadge: String?,
        vipBadgeExpires: String?,
        adFreeUntil: String?,
        doubleBoostUntilStr: String? = null
    ) {
        val now = java.time.Instant.now()
        val badgeExpInstant = parseDateRobust(vipBadgeExpires)
        val isBadgeStillValid = !vipBadge.isNullOrBlank() && (badgeExpInstant == null || badgeExpInstant.isAfter(now))

        userBadgeType = if (isBadgeStillValid) vipBadge!!.trim() else ""
        badgeExpiresAt = if (isBadgeStillValid) vipBadgeExpires else null

        val adFreeInstant = parseDateRobust(adFreeUntil)
        val latestAdFree = listOfNotNull(
            adFreeInstant?.takeIf { it.isAfter(now) },
            badgeExpInstant?.takeIf { isBadgeStillValid && it.isAfter(now) }
        ).maxOrNull()?.toString()

        val boostInstant = parseDateRobust(doubleBoostUntilStr)
        val isEliteActive = isBadgeStillValid && (userBadgeType.lowercase().contains("elite") || userBadgeType.lowercase() == "gold_vip")
        val latestBoost = listOfNotNull(
            boostInstant?.takeIf { it.isAfter(now) },
            badgeExpInstant?.takeIf { isEliteActive && it.isAfter(now) }
        ).maxOrNull()

        doubleBoostUntil = latestBoost?.toString()

        if (latestBoost != null) {
            val shopPrefs = context.getSharedPreferences("NexiPlayShop", Context.MODE_PRIVATE)
            val currentLocal = shopPrefs.getLong("boost_expiry", 0L)
            val remoteMs = latestBoost.toEpochMilli()
            if (remoteMs > currentLocal) {
                shopPrefs.edit().putLong("boost_expiry", remoteMs).apply()
            }
        }

        setAdFreeExpiry(context, latestAdFree)
    }

    fun setAdFreeExpiry(context: Context, expiry: String?) {
        val prefs = context.getSharedPreferences("NexiPlayPrefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            if (!expiry.isNullOrEmpty()) {
                putString("ad_free_until", expiry)
            } else {
                remove("ad_free_until")
            }
            if (userBadgeType.isNotEmpty()) {
                putString("user_badge_type", userBadgeType)
            } else {
                remove("user_badge_type")
            }
            if (!badgeExpiresAt.isNullOrEmpty()) {
                putString("badge_expires_at", badgeExpiresAt)
            } else {
                remove("badge_expires_at")
            }
            if (!doubleBoostUntil.isNullOrEmpty()) {
                putString("double_boost_until", doubleBoostUntil)
            } else {
                remove("double_boost_until")
            }
        }.apply()
        syncAdFreeStatus(context)
    }

    fun getAdFreeExpiry(context: Context): String? {
        val prefs = context.getSharedPreferences("NexiPlayPrefs", Context.MODE_PRIVATE)
        return prefs.getString("ad_free_until", null) ?: badgeExpiresAt
    }

    fun initStartIo(context: Context, appId: String) {
        val cleanId = appId.trim()
        if (cleanId.isNotEmpty() && !isStartIoInitialized) {
            isStartIoInitialized = true
            try {
                val appCtx = context.applicationContext
                StartAppSDK.init(appCtx, cleanId, false)
                StartAppSDK.enableReturnAds(false)
                StartAppSDK.setTestAdsEnabled(isTestAdsEnabled)
                if (!isAppOpenEnabled) {
                    StartAppAd.disableSplash()
                }
                android.util.Log.d("AdManager", "✅ Start.io initialized with appId=$cleanId, testAds=$isTestAdsEnabled")
            } catch (e: Exception) {
                isStartIoInitialized = false
                android.util.Log.e("AdManager", "❌ Start.io init failed", e)
            }
        }
    }

    fun initUnityLevelPlay(activity: Activity, appKey: String) {
        val cleanKey = appKey.trim()
        if (cleanKey.isNotEmpty() && !isUnityInitialized) {
            try {
                android.util.Log.d("AdManager", "🔵 Initializing Unity LevelPlay with appKey=$cleanKey")
                IronSource.init(
                    activity,
                    cleanKey,
                    IronSource.AD_UNIT.REWARDED_VIDEO,
                    IronSource.AD_UNIT.INTERSTITIAL,
                    IronSource.AD_UNIT.BANNER
                )
                IronSource.loadRewardedVideo()
                IronSource.loadInterstitial()
                isUnityInitialized = true
                android.util.Log.d("AdManager", "✅ Unity LevelPlay initialized successfully")
            } catch (e: Exception) {
                android.util.Log.e("AdManager", "❌ Unity LevelPlay init failed", e)
            }
        }
    }

    suspend fun loadConfig(supabase: SupabaseClient) {
        try {
            withContext(Dispatchers.IO) {
                val settings = supabase.postgrest["app_settings"]
                    .select()
                    .decodeSingleOrNull<AdSettings>()

                settings?.let {
                    if (!it.startioAppId.isNullOrBlank()) startioAppId = it.startioAppId.trim()
                    if (!it.unityAppKey.isNullOrBlank()) unityAppKey = it.unityAppKey.trim()
                    if (!it.unityBannerId.isNullOrBlank()) unityBannerId = it.unityBannerId.trim()
                    if (!it.unityInterstitialId.isNullOrBlank()) unityInterstitialId = it.unityInterstitialId.trim()
                    if (!it.unityRewardedId.isNullOrBlank()) unityRewardedId = it.unityRewardedId.trim()

                    currentNetwork = when (it.adNetwork?.trim()?.uppercase()) {
                        "STARTIO" -> AdNetwork.STARTIO
                        "UNITY" -> {
                            val isRawNumericGameId = unityAppKey.isNotBlank() && unityAppKey.all { ch -> ch.isDigit() }
                            if ((unityAppKey.isBlank() || isRawNumericGameId) && startioAppId.isNotBlank()) {
                                AdNetwork.STARTIO
                            } else {
                                AdNetwork.UNITY
                            }
                        }
                        "BOTH" -> AdNetwork.BOTH
                        else -> if (startioAppId.isNotBlank()) AdNetwork.STARTIO else AdNetwork.BOTH
                    }

                    isBannerEnabled = it.isBannerEnabled ?: true
                    isInterstitialEnabled = it.isInterstitialEnabled ?: true
                    isRewardedEnabled = it.isRewardedEnabled ?: true
                    isNativeEnabled = it.isNativeEnabled ?: false
                    isAppOpenEnabled = it.isAppOpenEnabled ?: false
                    isPremiumServerAdEnabled = it.isPremiumServerAdEnabled ?: true
                    isTestAdsEnabled = it.isTestAdsEnabled ?: false

                    // Adsterra Direct Link config
                    isDirectLinkAdsEnabled = it.isAdsEnabled ?: true
                    directLinkUrl = it.directLinkUrl.orEmpty().trim()
                    popunderUrl = it.popunderUrl.orEmpty().trim()
                    adFrequencySession = (it.adFrequencySession ?: 1).coerceIn(1, 10)
                    isDownloadVerificationEnabled = it.isDownloadVerificationEnabled ?: false
                    downloadAdUrl1 = it.downloadAdUrl1.orEmpty().trim()
                    downloadAdUrl2 = it.downloadAdUrl2.orEmpty().trim()
                    isStreamVerificationEnabled = it.isVerificationEnabled ?: false
                    verificationAdUrl1 = it.verificationAdUrl1.orEmpty().trim()
                    verificationAdUrl2 = it.verificationAdUrl2.orEmpty().trim()

                    coinPopupEnabled = it.coinPopupEnabled ?: true
                    if (!it.coinPopupTitle.isNullOrEmpty()) coinPopupTitle = it.coinPopupTitle
                    if (!it.coinPopupMessage.isNullOrEmpty()) coinPopupMessage = it.coinPopupMessage
                    if (!it.coinPopupButtonText.isNullOrEmpty()) coinPopupButtonText = it.coinPopupButtonText
                    if (!it.coinPopupAction.isNullOrEmpty()) coinPopupAction = it.coinPopupAction
                    coinPopupTriggerCount = it.coinPopupTriggerCount ?: 1

                    socialBarCode = it.socialBarCode
                    appEnabledServers = it.appEnabledServers

                    if (isStartIoInitialized) {
                        try { StartAppSDK.setTestAdsEnabled(isTestAdsEnabled) } catch (_: Exception) {}
                    }
                    if (!isAppOpenEnabled) {
                        try { StartAppAd.disableSplash() } catch (_: Exception) {}
                    }

                    isConfigLoaded = true
                    _configLoadedFlow.value = true

                    // Trigger deferred Ad SDK initialization only after Splash finishes
                    if (isStartupReadyForAds) {
                        val act = pendingActivity?.get()
                        if (act != null) {
                            withContext(Dispatchers.Main) {
                                initializeConfiguredAdSdks(act)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun formatIronSourceError(error: IronSourceError?): String {
        if (error == null) return "Ad failed to load"
        val code = error.errorCode
        val msg = error.errorMessage ?: ""
        return when (code) {
            508 -> "No ads currently available. Please try again in a few moments."
            510 -> "Ad is still loading. Please try again in a few seconds."
            520, 524 -> "Ad network initializing. Please retry."
            else -> {
                if (msg.contains("http://", ignoreCase = true) || msg.contains("https://", ignoreCase = true)) {
                    "Ad provider is initializing. Please try again shortly."
                } else {
                    msg.ifEmpty { "Failed to load ad (Error $code)" }
                }
            }
        }
    }

    private fun normalizeHttpUrl(raw: String?): String? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isBlank()) return null
        return if (trimmed.startsWith("https://", ignoreCase = true) || trimmed.startsWith("http://", ignoreCase = true)) {
            trimmed
        } else {
            null
        }
    }

    fun getDownloadDirectLinkUrl(): String? {
        return normalizeHttpUrl(downloadAdUrl1)
            ?: normalizeHttpUrl(downloadAdUrl2)
            ?: normalizeHttpUrl(directLinkUrl)
    }

    fun getStreamDirectLinkUrl(): String? {
        return normalizeHttpUrl(verificationAdUrl1)
            ?: normalizeHttpUrl(verificationAdUrl2)
            ?: normalizeHttpUrl(directLinkUrl)
    }

    fun getRewardedDirectLinkUrl(): String? {
        return normalizeHttpUrl(directLinkUrl)
            ?: normalizeHttpUrl(downloadAdUrl1)
            ?: normalizeHttpUrl(verificationAdUrl1)
    }

    fun hasDirectLinkConfigured(): Boolean {
        return isDirectLinkAdsEnabled && getRewardedDirectLinkUrl() != null
    }

    /**
     * Opens an Adsterra Direct Link in Chrome Custom Tabs / external browser so cookies & CPM track natively.
     */
    fun openExternalDirectLink(context: Context, url: String): Boolean {
        val cleanUrl = normalizeHttpUrl(url) ?: return false
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(cleanUrl)).apply {
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                // Hint Chrome Custom Tabs when available for smoother in-app return UX
                val customTabExtras = android.os.Bundle().apply {
                    putBinder("android.support.customtabs.extra.SESSION", null)
                }
                putExtras(customTabExtras)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            android.util.Log.e("AdManager", "❌ Failed to open direct link: $cleanUrl", e)
            false
        }
    }

    private fun getDownloadPassWindowMs(): Long {
        return when {
            adFrequencySession >= 5 -> 3 * 60 * 1000L
            adFrequencySession >= 2 -> 5 * 60 * 1000L
            else -> DOWNLOAD_AD_PASS_WINDOW_MS
        }
    }

    private fun getStreamPassWindowMs(): Long {
        return when {
            adFrequencySession >= 5 -> 4 * 60 * 1000L
            adFrequencySession >= 2 -> 6 * 60 * 1000L
            else -> STREAM_AD_PASS_WINDOW_MS
        }
    }

    fun showDirectLinkOverlay(
        url: String,
        title: String,
        durationSeconds: Int,
        actionLabel: String,
        onCompleted: () -> Unit,
        onCancelled: () -> Unit = {}
    ): Boolean {
        val cleanUrl = normalizeHttpUrl(url) ?: return false
        mainHandler.post {
            _directLinkOverlay.value = DirectLinkOverlaySession(
                id = System.currentTimeMillis(),
                url = cleanUrl,
                title = title,
                durationSeconds = durationSeconds.coerceAtLeast(3),
                actionLabel = actionLabel,
                onCompleted = onCompleted,
                onCancelled = onCancelled
            )
        }
        return true
    }

    fun dismissDirectLinkOverlay(completed: Boolean) {
        val session = _directLinkOverlay.value ?: return
        _directLinkOverlay.value = null
        mainHandler.post {
            if (completed) {
                session.onCompleted()
            } else {
                session.onCancelled()
            }
        }
    }

    /**
     * Frequency-capped Adsterra Direct Link overlay when user taps "Watch Now" or Episode "Watch".
     * Shows a 5s live countdown overlay inside the app before opening the player.
     */
    fun runWithStreamAdGate(context: Context, onProceedToWatch: () -> Unit) {
        if (isUserPremium()) {
            onProceedToWatch()
            return
        }

        val streamUrl = getStreamDirectLinkUrl()
        val gateEnabled = isStreamVerificationEnabled || isDirectLinkAdsEnabled
        if (streamUrl.isNullOrBlank() || !gateEnabled) {
            onProceedToWatch()
            return
        }

        val prefs = context.getSharedPreferences(PREFS_ADS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastStreamMs = prefs.getLong(KEY_LAST_STREAM_AD_MS, 0L)
        val lastAnyMs = prefs.getLong(KEY_LAST_ANY_DIRECT_LINK_MS, 0L)
        if (now - lastStreamMs < getStreamPassWindowMs() || now - lastAnyMs < MIN_DIRECT_LINK_GAP_MS) {
            onProceedToWatch()
            return
        }

        showDirectLinkOverlay(
            url = streamUrl,
            title = "Preparing Stream",
            durationSeconds = 5,
            actionLabel = "Watch Now",
            onCompleted = {
                val ts = System.currentTimeMillis()
                prefs.edit()
                    .putLong(KEY_LAST_STREAM_AD_MS, ts)
                    .putLong(KEY_LAST_ANY_DIRECT_LINK_MS, ts)
                    .apply()
                onProceedToWatch()
            }
        )
    }

    /**
     * Frequency-capped Adsterra Direct Link overlay before starting a movie/episode download.
     * Shows a 6s live countdown overlay inside the app, then starts the download.
     */
    fun runWithDownloadAdGate(context: Context, onUnlocked: () -> Unit) {
        if (isUserPremium()) {
            onUnlocked()
            return
        }
        val directUrl = getDownloadDirectLinkUrl()
        val gateEnabled = isDownloadVerificationEnabled || isDirectLinkAdsEnabled
        if (directUrl.isNullOrBlank() || !gateEnabled) {
            onUnlocked()
            return
        }

        val prefs = context.getSharedPreferences(PREFS_ADS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastMs = prefs.getLong(KEY_LAST_DOWNLOAD_AD_MS, 0L)
        val lastAnyMs = prefs.getLong(KEY_LAST_ANY_DIRECT_LINK_MS, 0L)
        if (now - lastMs < getDownloadPassWindowMs() || now - lastAnyMs < MIN_DIRECT_LINK_GAP_MS) {
            onUnlocked()
            return
        }

        showDirectLinkOverlay(
            url = directUrl,
            title = "Unlocking Fast Download",
            durationSeconds = 6,
            actionLabel = "Download Now",
            onCompleted = {
                val ts = System.currentTimeMillis()
                prefs.edit()
                    .putLong(KEY_LAST_DOWNLOAD_AD_MS, ts)
                    .putLong(KEY_LAST_ANY_DIRECT_LINK_MS, ts)
                    .apply()
                onUnlocked()
            }
        )
    }

    /**
     * Intentional Coin Center bonus explorer task (10s overlay countdown + 10m cooldown).
     * Rewards strictly when the full 10s timer finishes.
     */
    fun triggerDirectLinkCoinTask(
        context: Context,
        onRewarded: () -> Unit,
        onUnavailableOrCooldown: (String) -> Unit
    ) {
        val url = getRewardedDirectLinkUrl()
        if (!isDirectLinkAdsEnabled || url.isNullOrBlank()) {
            onUnavailableOrCooldown("Bonus task is currently unavailable.")
            return
        }
        val prefs = context.getSharedPreferences(PREFS_ADS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastMs = prefs.getLong(KEY_LAST_SPONSOR_TASK_MS, 0L)
        val remainingMs = SPONSOR_TASK_COOLDOWN_MS - (now - lastMs)
        if (remainingMs > 0L) {
            val mins = maxOf(1L, (remainingMs + 59_999L) / 60_000L)
            onUnavailableOrCooldown("Bonus task available again in ${mins}m.")
            return
        }

        showDirectLinkOverlay(
            url = url,
            title = "Bonus Coin Verification",
            durationSeconds = 10,
            actionLabel = "Claim Coins",
            onCompleted = {
                val ts = System.currentTimeMillis()
                prefs.edit()
                    .putLong(KEY_LAST_SPONSOR_TASK_MS, ts)
                    .putLong(KEY_LAST_ANY_DIRECT_LINK_MS, ts)
                    .apply()
                onRewarded()
            },
            onCancelled = {
                onUnavailableOrCooldown("Please stay for the full 10s timer to claim coins.")
            }
        )
    }

    fun showRewardedAd(
        context: Context,
        onRewarded: () -> Unit,
        onFailed: (String) -> Unit,
        allowPremiumBypass: Boolean = false
    ) {
        if (allowPremiumBypass && isUserPremium()) {
            mainHandler.post { onRewarded() }
            return
        }
        val activity = context.findActivity()
        if (activity == null || !isRewardedEnabled) {
            mainHandler.post { onFailed("Rewarded ads are currently disabled") }
            return
        }

        if (startioAppId.isNotEmpty() && !isStartIoInitialized) {
            initStartIo(activity.applicationContext, startioAppId)
        }

        when (currentNetwork) {
            AdNetwork.STARTIO -> loadAndShowStartIo(
                activity = activity,
                onRewarded = onRewarded,
                onFailed = { startioErr ->
                    tryAdsterraRewardFallback(activity, startioErr, onRewarded, onFailed)
                }
            )
            AdNetwork.UNITY -> {
                loadAndShowUnityRewarded(
                    activity = activity,
                    onRewarded = onRewarded,
                    onFailed = { unityErr ->
                        // Safety fallback to Start.io then Direct Link Overlay if configured
                        if (startioAppId.isNotEmpty()) {
                            loadAndShowStartIo(
                                activity = activity,
                                onRewarded = onRewarded,
                                onFailed = { startioErr ->
                                    tryAdsterraRewardFallback(activity, "$unityErr | $startioErr", onRewarded, onFailed)
                                }
                            )
                        } else {
                            tryAdsterraRewardFallback(activity, unityErr, onRewarded, onFailed)
                        }
                    }
                )
            }
            AdNetwork.BOTH -> {
                // Start.io goes FIRST (higher BD fill & APK compatibility), Unity is secondary fallback
                loadAndShowStartIo(
                    activity = activity,
                    onRewarded = onRewarded,
                    onFailed = { startioErr ->
                        if (unityAppKey.isNotEmpty()) {
                            loadAndShowUnityRewarded(
                                activity = activity,
                                onRewarded = onRewarded,
                                onFailed = { unityErr ->
                                    tryAdsterraRewardFallback(
                                        activity,
                                        "Start.io: $startioErr | Unity: $unityErr",
                                        onRewarded,
                                        onFailed
                                    )
                                }
                            )
                        } else {
                            tryAdsterraRewardFallback(activity, startioErr, onRewarded, onFailed)
                        }
                    }
                )
            }
        }
    }

    private fun tryAdsterraRewardFallback(
        activity: Activity,
        previousError: String,
        onRewarded: () -> Unit,
        onFailed: (String) -> Unit
    ) {
        val fallbackUrl = if (isDirectLinkAdsEnabled) getRewardedDirectLinkUrl() else null
        if (fallbackUrl != null && !activity.isFinishing && !activity.isDestroyed) {
            showDirectLinkOverlay(
                url = fallbackUrl,
                title = "Reward Verification",
                durationSeconds = 10,
                actionLabel = "Claim Reward",
                onCompleted = { onRewarded() },
                onCancelled = { onFailed("Please complete the 10s ad timer to earn the reward.") }
            )
        } else {
            mainHandler.post { onFailed(previousError) }
        }
    }

    private fun loadAndShowUnityRewarded(
        activity: Activity,
        onRewarded: () -> Unit,
        onFailed: (String) -> Unit
    ) {
        if (unityAppKey.isEmpty()) {
            onFailed("Unity LevelPlay App Key is not configured")
            return
        }
        initUnityLevelPlay(activity, unityAppKey)

        var hasTriggered = false
        var hasShown = false
        var wasRewarded = false

        val showAd = {
            if (!hasShown) {
                hasShown = true
                android.util.Log.d("AdManager", "🎬 Showing Unity rewarded video")
                if (unityRewardedId.isNotEmpty()) {
                    IronSource.showRewardedVideo(unityRewardedId)
                } else {
                    IronSource.showRewardedVideo()
                }
            }
        }

        val timeoutRunnable = Runnable {
            if (!hasTriggered) {
                hasTriggered = true
                android.util.Log.w("AdManager", "⏰ Unity rewarded ad timed out")
                onFailed("Unity ad request timed out.")
            }
        }
        mainHandler.postDelayed(timeoutRunnable, 12000L)

        IronSource.setLevelPlayRewardedVideoListener(object : LevelPlayRewardedVideoListener {
            override fun onAdOpened(adInfo: AdInfo?) {
                mainHandler.removeCallbacks(timeoutRunnable)
            }
            override fun onAdShowFailed(error: IronSourceError?, adInfo: AdInfo?) {
                val formatted = formatIronSourceError(error)
                mainHandler.removeCallbacks(timeoutRunnable)
                if (!hasTriggered) {
                    hasTriggered = true
                    onFailed(formatted)
                }
            }
            override fun onAdClicked(placement: Placement?, adInfo: AdInfo?) {}
            override fun onAdRewarded(placement: Placement?, adInfo: AdInfo?) {
                wasRewarded = true
            }
            override fun onAdClosed(adInfo: AdInfo?) {
                mainHandler.removeCallbacks(timeoutRunnable)
                IronSource.loadRewardedVideo()
                if (!hasTriggered) {
                    hasTriggered = true
                    if (wasRewarded) {
                        onRewarded()
                    } else {
                        onFailed("Please watch the full ad to earn the reward.")
                    }
                }
            }
            override fun onAdAvailable(adInfo: AdInfo?) {
                if (!hasTriggered) {
                    showAd()
                }
            }
            override fun onAdUnavailable() {
                mainHandler.removeCallbacks(timeoutRunnable)
                if (!hasTriggered) {
                    hasTriggered = true
                    onFailed("No Unity ads available.")
                }
            }
        })

        if (IronSource.isRewardedVideoAvailable()) {
            showAd()
        } else {
            IronSource.loadRewardedVideo()
        }
    }

    /**
     * 3-Stage Start.io Rewarded Loader:
     * 1) REWARDED_VIDEO -> 2) VIDEO -> 3) AUTOMATIC (non-video fullscreen interactive/display ad fallback).
     * Guarantees single-shot main-thread resolution via AdDisplayListener & timeout guard.
     */
    private fun loadAndShowStartIo(
        activity: Activity,
        onRewarded: () -> Unit,
        onFailed: (String) -> Unit
    ) {
        if (startioAppId.isEmpty()) {
            mainHandler.post { onFailed("Start.io App ID is empty") }
            return
        }
        initStartIo(activity.applicationContext, startioAppId)

        val stages = listOf(
            StartAppAd.AdMode.REWARDED_VIDEO,
            StartAppAd.AdMode.VIDEO,
            StartAppAd.AdMode.AUTOMATIC
        )
        runStartIoRewardStage(activity, stages, 0, onRewarded, onFailed)
    }

    private fun runStartIoRewardStage(
        activity: Activity,
        stages: List<StartAppAd.AdMode>,
        stageIndex: Int,
        onRewarded: () -> Unit,
        onFailed: (String) -> Unit,
        lastError: String = "No Start.io ads available right now."
    ) {
        if (stageIndex >= stages.size || activity.isFinishing || activity.isDestroyed) {
            mainHandler.post { onFailed(lastError) }
            return
        }

        val mode = stages[stageIndex]
        val isVideoStage = mode == StartAppAd.AdMode.REWARDED_VIDEO || mode == StartAppAd.AdMode.VIDEO
        val startAppAd = StartAppAd(activity)
        val stageResolved = AtomicBoolean(false)
        val videoCompleted = AtomicBoolean(false)
        val adDisplayed = AtomicBoolean(false)
        val adClicked = AtomicBoolean(false)
        var displayedAtMs = 0L

        val fallbackToNext: (String) -> Unit = { err ->
            if (stageResolved.compareAndSet(false, true)) {
                android.util.Log.w("AdManager", "⚠️ Start.io stage $mode failed: $err")
                runStartIoRewardStage(activity, stages, stageIndex + 1, onRewarded, onFailed, err)
            }
        }

        val timeoutRunnable = Runnable {
            fallbackToNext("Start.io $mode timed out")
        }
        mainHandler.postDelayed(timeoutRunnable, 10000L)

        if (isVideoStage) {
            startAppAd.setVideoListener {
                videoCompleted.set(true)
            }
        }

        startAppAd.loadAd(mode, object : AdEventListener {
            override fun onReceiveAd(ad: com.startapp.sdk.adsbase.Ad) {
                mainHandler.removeCallbacks(timeoutRunnable)
                if (stageResolved.get()) return
                mainHandler.post {
                    if (activity.isFinishing || activity.isDestroyed) {
                        fallbackToNext("Activity closed")
                        return@post
                    }
                    val shown = startAppAd.showAd(object : AdDisplayListener {
                        override fun adDisplayed(shownAd: com.startapp.sdk.adsbase.Ad?) {
                            displayedAtMs = System.currentTimeMillis()
                            adDisplayed.set(true)
                        }

                        override fun adClicked(shownAd: com.startapp.sdk.adsbase.Ad?) {
                            adClicked.set(true)
                        }

                        override fun adNotDisplayed(shownAd: com.startapp.sdk.adsbase.Ad?) {
                            fallbackToNext("Start.io $mode could not be displayed")
                        }

                        override fun adHidden(shownAd: com.startapp.sdk.adsbase.Ad?) {
                            if (!stageResolved.compareAndSet(false, true)) return
                            if (isVideoStage) {
                                if (videoCompleted.get()) {
                                    onRewarded()
                                } else {
                                    onFailed("Please watch the full ad to earn the reward.")
                                }
                            } else {
                                // Non-video AUTOMATIC display/interactive reward fallback (require genuine display/view)
                                val viewedMs = if (displayedAtMs > 0L) System.currentTimeMillis() - displayedAtMs else 0L
                                if (adDisplayed.get() && (adClicked.get() || viewedMs >= 2000L)) {
                                    onRewarded()
                                } else if (adDisplayed.get()) {
                                    onFailed("Please view the ad for a moment before closing.")
                                } else {
                                    runStartIoRewardStage(
                                        activity,
                                        stages,
                                        stageIndex + 1,
                                        onRewarded,
                                        onFailed,
                                        "Ad was dismissed before displaying"
                                    )
                                }
                            }
                        }
                    })
                    if (!shown) {
                        fallbackToNext("Start.io $mode returned false from showAd")
                    }
                }
            }

            override fun onFailedToReceiveAd(ad: com.startapp.sdk.adsbase.Ad?) {
                mainHandler.removeCallbacks(timeoutRunnable)
                fallbackToNext(ad?.errorMessage ?: "Start.io $mode no fill")
            }
        })
    }

    var contentAdCounter = 0
    fun showAlternatingAd(context: Context, onAdFinished: () -> Unit) {
        if (isUserPremium()) {
            onAdFinished()
            return
        }
        contentAdCounter++
        if (contentAdCounter % 2 == 0) {
            showRewardedAd(
                context = context,
                onRewarded = { onAdFinished() },
                onFailed = {
                    showInterstitialWithCooldown(context, cooldownMs = 60_000L, onFinished = onAdFinished)
                }
            )
        } else {
            showInterstitialWithCooldown(context, cooldownMs = 60_000L, onFinished = onAdFinished)
        }
    }

    /**
     * Shows an interstitial at most once per [cooldownMs] so user navigation & watching stay smooth.
     */
    fun showInterstitialWithCooldown(
        context: Context,
        cooldownMs: Long = DEFAULT_INTERSTITIAL_COOLDOWN_MS,
        onFinished: () -> Unit
    ) {
        if (!isInterstitialEnabled || isUserPremium()) {
            onFinished()
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastInterstitialShownAtMs < cooldownMs) {
            onFinished()
            return
        }
        lastInterstitialShownAtMs = now
        showInterstitialAd(context, onFinished)
    }

    fun showInterstitialAd(context: Context, onClosed: () -> Unit) {
        if (!isInterstitialEnabled || isUserPremium()) {
            mainHandler.post { onClosed() }
            return
        }
        val activity = context.findActivity()
        if (activity == null) {
            mainHandler.post { onClosed() }
            return
        }
        if (startioAppId.isNotEmpty() && !isStartIoInitialized) {
            initStartIo(activity.applicationContext, startioAppId)
        }

        val finished = AtomicBoolean(false)
        val wrappedOnClosed = {
            if (finished.compareAndSet(false, true)) {
                mainHandler.post {
                    onClosed()
                    checkAndTriggerUpsellPopup()
                }
            }
        }

        when (currentNetwork) {
            AdNetwork.STARTIO -> loadAndShowStartIoInterstitial(activity, wrappedOnClosed)
            AdNetwork.UNITY -> {
                loadAndShowUnityInterstitial(activity, onClosed = wrappedOnClosed, onFailed = {
                    if (startioAppId.isNotEmpty()) {
                        loadAndShowStartIoInterstitial(activity, wrappedOnClosed)
                    } else {
                        wrappedOnClosed()
                    }
                })
            }
            AdNetwork.BOTH -> {
                loadAndShowStartIoInterstitial(activity, onClosed = wrappedOnClosed, onFailed = {
                    if (unityAppKey.isNotEmpty()) {
                        loadAndShowUnityInterstitial(activity, onClosed = wrappedOnClosed, onFailed = {
                            wrappedOnClosed()
                        })
                    } else {
                        wrappedOnClosed()
                    }
                })
            }
        }
    }

    private fun loadAndShowUnityInterstitial(
        activity: Activity,
        onClosed: () -> Unit,
        onFailed: (() -> Unit)? = null
    ) {
        if (unityAppKey.isEmpty()) {
            onFailed?.invoke() ?: onClosed()
            return
        }
        initUnityLevelPlay(activity, unityAppKey)

        val settled = AtomicBoolean(false)
        val timeoutRunnable = Runnable {
            if (settled.compareAndSet(false, true)) {
                onFailed?.invoke() ?: onClosed()
            }
        }
        mainHandler.postDelayed(timeoutRunnable, 8000L)

        val showInterstitial = {
            if (unityInterstitialId.isNotEmpty()) {
                IronSource.showInterstitial(unityInterstitialId)
            } else {
                IronSource.showInterstitial()
            }
        }

        IronSource.setLevelPlayInterstitialListener(object : LevelPlayInterstitialListener {
            override fun onAdReady(adInfo: AdInfo?) {
                mainHandler.removeCallbacks(timeoutRunnable)
                if (!settled.get()) showInterstitial()
            }
            override fun onAdLoadFailed(error: IronSourceError?) {
                mainHandler.removeCallbacks(timeoutRunnable)
                if (settled.compareAndSet(false, true)) onFailed?.invoke() ?: onClosed()
            }
            override fun onAdOpened(adInfo: AdInfo?) {
                mainHandler.removeCallbacks(timeoutRunnable)
            }
            override fun onAdShowSucceeded(adInfo: AdInfo?) {}
            override fun onAdShowFailed(error: IronSourceError?, adInfo: AdInfo?) {
                mainHandler.removeCallbacks(timeoutRunnable)
                if (settled.compareAndSet(false, true)) onFailed?.invoke() ?: onClosed()
            }
            override fun onAdClicked(adInfo: AdInfo?) {}
            override fun onAdClosed(adInfo: AdInfo?) {
                mainHandler.removeCallbacks(timeoutRunnable)
                IronSource.loadInterstitial()
                if (settled.compareAndSet(false, true)) onClosed()
            }
        })
        if (IronSource.isInterstitialReady()) {
            mainHandler.removeCallbacks(timeoutRunnable)
            showInterstitial()
        } else {
            IronSource.loadInterstitial()
        }
    }

    private fun loadAndShowStartIoInterstitial(
        activity: Activity,
        onClosed: () -> Unit,
        onFailed: (() -> Unit)? = null
    ) {
        if (startioAppId.isEmpty() || activity.isFinishing || activity.isDestroyed) {
            onFailed?.invoke() ?: onClosed()
            return
        }
        initStartIo(activity.applicationContext, startioAppId)

        val settled = AtomicBoolean(false)
        val finishFail = {
            if (settled.compareAndSet(false, true)) {
                onFailed?.invoke() ?: onClosed()
            }
        }
        val finishClose = {
            if (settled.compareAndSet(false, true)) {
                onClosed()
            }
        }

        val timeoutRunnable = Runnable { finishFail() }
        mainHandler.postDelayed(timeoutRunnable, 7500L)

        val startAppAd = StartAppAd(activity)
        startAppAd.loadAd(StartAppAd.AdMode.AUTOMATIC, object : AdEventListener {
            override fun onReceiveAd(ad: com.startapp.sdk.adsbase.Ad) {
                mainHandler.removeCallbacks(timeoutRunnable)
                if (settled.get()) return
                mainHandler.post {
                    val shown = startAppAd.showAd(object : AdDisplayListener {
                        override fun adHidden(p0: com.startapp.sdk.adsbase.Ad?) { finishClose() }
                        override fun adDisplayed(p0: com.startapp.sdk.adsbase.Ad?) {}
                        override fun adClicked(p0: com.startapp.sdk.adsbase.Ad?) {}
                        override fun adNotDisplayed(p0: com.startapp.sdk.adsbase.Ad?) { finishFail() }
                    })
                    if (!shown) finishFail()
                }
            }
            override fun onFailedToReceiveAd(ad: com.startapp.sdk.adsbase.Ad?) {
                mainHandler.removeCallbacks(timeoutRunnable)
                finishFail()
            }
        })
    }

    private fun createStartIoBannerContainer(context: Context, isMrec: Boolean): View? {
        if (startioAppId.isEmpty()) return null
        initStartIo(context.applicationContext, startioAppId)

        val container = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            visibility = View.GONE
        }

        val listener = object : BannerListener {
            override fun onReceiveAd(bannerView: View?) {
                mainHandler.post {
                    container.visibility = View.VISIBLE
                    bannerView?.visibility = View.VISIBLE
                }
            }
            override fun onFailedToReceiveAd(bannerView: View?) {
                mainHandler.post {
                    container.visibility = View.GONE
                }
            }
            override fun onImpression(bannerView: View?) {}
            override fun onClick(bannerView: View?) {}
        }

        val banner = (if (isMrec) Mrec(context) else Banner(context)).apply {
            setBannerListener(listener)
        }
        banner.visibility = View.GONE
        container.addView(
            banner,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL
            )
        )
        banner.loadAd()
        return container
    }

    fun getBannerAdView(context: Context): View? {
        if (!isBannerEnabled || isUserPremium()) return null
        val activity = context.findActivity()

        return when (currentNetwork) {
            AdNetwork.UNITY -> {
                if (activity != null && unityAppKey.isNotEmpty()) {
                    initUnityLevelPlay(activity, unityAppKey)
                    val banner = IronSource.createBanner(activity, ISBannerSize.BANNER)
                    if (unityBannerId.isNotEmpty()) {
                        IronSource.loadBanner(banner, unityBannerId)
                    } else {
                        IronSource.loadBanner(banner)
                    }
                    banner
                } else {
                    createStartIoBannerContainer(context, isMrec = false)
                }
            }
            AdNetwork.STARTIO, AdNetwork.BOTH -> {
                createStartIoBannerContainer(context, isMrec = false) ?: run {
                    if (currentNetwork == AdNetwork.BOTH && activity != null && unityAppKey.isNotEmpty()) {
                        initUnityLevelPlay(activity, unityAppKey)
                        val banner = IronSource.createBanner(activity, ISBannerSize.BANNER)
                        if (unityBannerId.isNotEmpty()) IronSource.loadBanner(banner, unityBannerId)
                        else IronSource.loadBanner(banner)
                        banner
                    } else null
                }
            }
        }
    }

    fun getNativeAdView(context: Context): View? {
        if (!isNativeEnabled || isUserPremium()) return null
        return when (currentNetwork) {
            AdNetwork.STARTIO, AdNetwork.BOTH -> createStartIoBannerContainer(context, isMrec = true)
            AdNetwork.UNITY -> createStartIoBannerContainer(context, isMrec = true)
        }
    }

    fun showAppOpenAd(context: Context, onClosed: () -> Unit) {
        if (!isAppOpenEnabled || isUserPremium()) {
            mainHandler.post { onClosed() }
            return
        }
        val activity = context.findActivity()
        if (activity == null) {
            mainHandler.post { onClosed() }
            return
        }

        val done = AtomicBoolean(false)
        val finish = {
            if (done.compareAndSet(false, true)) {
                mainHandler.post { onClosed() }
            }
        }

        when (currentNetwork) {
            AdNetwork.STARTIO, AdNetwork.BOTH -> loadAndShowStartIoInterstitial(activity, finish)
            AdNetwork.UNITY -> showInterstitialAd(context, finish)
        }
    }
}
