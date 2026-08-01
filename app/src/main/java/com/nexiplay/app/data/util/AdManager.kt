package com.nexiplay.app.data.util

import android.app.Activity
import android.content.Context
import android.view.View
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.StartAppSDK
import com.startapp.sdk.adsbase.adlisteners.AdEventListener
import com.startapp.sdk.adsbase.adlisteners.VideoListener
import com.ironsource.mediationsdk.IronSource
import com.ironsource.mediationsdk.ISBannerSize
import com.ironsource.mediationsdk.IronSourceBannerLayout
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
    @SerialName("coin_popup_enabled") val coinPopupEnabled: Boolean? = true,
    @SerialName("coin_popup_title") val coinPopupTitle: String? = null,
    @SerialName("coin_popup_message") val coinPopupMessage: String? = null,
    @SerialName("coin_popup_button_text") val coinPopupButtonText: String? = null,
    @SerialName("coin_popup_action") val coinPopupAction: String? = null,
    @SerialName("coin_popup_trigger_count") val coinPopupTriggerCount: Int? = 1,
    @SerialName("social_bar_code") val socialBarCode: String? = null,
    @SerialName("app_enabled_servers") val appEnabledServers: String? = null
)

object AdManager {
    var currentNetwork = AdNetwork.BOTH
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
    var isUserAdFree = false
    var userBadgeType = ""  // "vip", "elite_pro", "gold_vip", "elite"

    private var isUnityInitialized = false
    
    /** Returns true if user has any active premium badge */
    fun isUserPremium(): Boolean {
        return isUserAdFree || userBadgeType.isNotEmpty()
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
        if (!isUserAdFree && coinPopupEnabled) {
            adDismissCounter++
            if (adDismissCounter % maxOf(1, coinPopupTriggerCount) == 0) {
                onShowUpsellPopup?.invoke(coinPopupTitle, coinPopupMessage, coinPopupButtonText, coinPopupAction)
            }
        }
    }

    fun parseDateRobust(dateStr: String?): java.time.Instant? {
        if (dateStr.isNullOrEmpty()) return null
        return try {
            var fixed = dateStr.replace(" ", "T")
            if (fixed.endsWith("+00:00")) {
                fixed = fixed.replace("+00:00", "Z")
            }
            if (!fixed.contains("Z") && !fixed.contains("+")) {
                fixed += "Z"
            }
            java.time.Instant.parse(fixed)
        } catch (e: Exception) {
            null
        }
    }

    fun syncAdFreeStatus(context: Context) {
        val prefs = context.getSharedPreferences("NexiPlayPrefs", Context.MODE_PRIVATE)
        val expiry = prefs.getString("ad_free_until", null)
        
        val savedBadge = prefs.getString("user_badge_type", null)
        if (!savedBadge.isNullOrEmpty()) {
            userBadgeType = savedBadge
        }
        
        if (!expiry.isNullOrEmpty()) {
            try {
                val parsed = parseDateRobust(expiry)
                if (parsed != null && parsed.isAfter(java.time.Instant.now())) {
                    isUserAdFree = true
                    return
                } else {
                    userBadgeType = ""
                    prefs.edit().remove("user_badge_type").apply()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        isUserAdFree = false
    }

    fun setAdFreeExpiry(context: Context, expiry: String?) {
        val prefs = context.getSharedPreferences("NexiPlayPrefs", Context.MODE_PRIVATE)
        prefs.edit().putString("ad_free_until", expiry).apply()
        if (userBadgeType.isNotEmpty()) {
            prefs.edit().putString("user_badge_type", userBadgeType).apply()
        }
        syncAdFreeStatus(context)
    }

    fun getAdFreeExpiry(context: Context): String? {
        val prefs = context.getSharedPreferences("NexiPlayPrefs", Context.MODE_PRIVATE)
        return prefs.getString("ad_free_until", null)
    }

    fun initStartIo(context: Context, appId: String) {
        if (appId.isNotEmpty()) {
            StartAppSDK.init(context, appId, false)
            StartAppSDK.setTestAdsEnabled(isTestAdsEnabled)
        }
    }

    fun initUnityLevelPlay(activity: Activity, appKey: String) {
        if (appKey.isNotEmpty() && !isUnityInitialized) {
            try {
                IronSource.init(
                    activity,
                    appKey,
                    IronSource.AD_UNIT.REWARDED_VIDEO,
                    IronSource.AD_UNIT.INTERSTITIAL,
                    IronSource.AD_UNIT.BANNER
                )
                IronSource.loadRewardedVideo()
                isUnityInitialized = true
            } catch (e: Exception) {
                e.printStackTrace()
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
                    currentNetwork = when (it.adNetwork?.uppercase()) {
                        "STARTIO" -> AdNetwork.STARTIO
                        "UNITY" -> AdNetwork.UNITY
                        else -> AdNetwork.BOTH
                    }
                    if (!it.startioAppId.isNullOrEmpty()) startioAppId = it.startioAppId
                    if (!it.unityAppKey.isNullOrEmpty()) unityAppKey = it.unityAppKey
                    if (!it.unityBannerId.isNullOrEmpty()) unityBannerId = it.unityBannerId
                    if (!it.unityInterstitialId.isNullOrEmpty()) unityInterstitialId = it.unityInterstitialId
                    if (!it.unityRewardedId.isNullOrEmpty()) unityRewardedId = it.unityRewardedId

                    isBannerEnabled = it.isBannerEnabled ?: true
                    isInterstitialEnabled = it.isInterstitialEnabled ?: true
                    isRewardedEnabled = it.isRewardedEnabled ?: true
                    isNativeEnabled = it.isNativeEnabled ?: false
                    isAppOpenEnabled = it.isAppOpenEnabled ?: false
                    isPremiumServerAdEnabled = it.isPremiumServerAdEnabled ?: true
                    isTestAdsEnabled = it.isTestAdsEnabled ?: true
                    coinPopupEnabled = it.coinPopupEnabled ?: true
                    if (!it.coinPopupTitle.isNullOrEmpty()) coinPopupTitle = it.coinPopupTitle
                    if (!it.coinPopupMessage.isNullOrEmpty()) coinPopupMessage = it.coinPopupMessage
                    if (!it.coinPopupButtonText.isNullOrEmpty()) coinPopupButtonText = it.coinPopupButtonText
                    if (!it.coinPopupAction.isNullOrEmpty()) coinPopupAction = it.coinPopupAction
                    coinPopupTriggerCount = it.coinPopupTriggerCount ?: 1

                    socialBarCode = it.socialBarCode
                    appEnabledServers = it.appEnabledServers

                    if (!isAppOpenEnabled) {
                        StartAppAd.disableSplash()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun showRewardedAd(
        context: Context,
        onRewarded: () -> Unit,
        onFailed: (String) -> Unit
    ) {
        val activity = context as? Activity
        if (activity == null || !isRewardedEnabled) {
            onFailed("Ads are disabled or activity not found")
            return
        }

        if (unityAppKey.isNotEmpty()) {
            initUnityLevelPlay(activity, unityAppKey)
        }

        when (currentNetwork) {
            AdNetwork.STARTIO -> loadAndShowStartIo(activity, onRewarded, onFailed)
            AdNetwork.UNITY, AdNetwork.BOTH -> {
                loadAndShowUnityRewarded(activity, onRewarded, onFailed = { unityErr ->
                    loadAndShowStartIo(activity, onRewarded, onFailed = { startioErr ->
                        onFailed("Unity LevelPlay: $unityErr | Start.io: $startioErr")
                    })
                })
            }
        }
    }

    private fun loadAndShowUnityRewarded(
        activity: Activity,
        onRewarded: () -> Unit,
        onFailed: (String) -> Unit
    ) {
        if (unityAppKey.isEmpty()) {
            loadAndShowStartIo(activity, onRewarded, onFailed)
            return
        }
        initUnityLevelPlay(activity, unityAppKey)

        var hasTriggered = false
        IronSource.setLevelPlayRewardedVideoListener(object : LevelPlayRewardedVideoListener {
            override fun onAdOpened(adInfo: AdInfo?) {}
            override fun onAdShowFailed(error: IronSourceError?, adInfo: AdInfo?) {
                if (!hasTriggered) {
                    hasTriggered = true
                    loadAndShowStartIo(activity, onRewarded, onFailed)
                }
            }
            override fun onAdClicked(placement: Placement?, adInfo: AdInfo?) {}
            override fun onAdRewarded(placement: Placement?, adInfo: AdInfo?) {
                if (!hasTriggered) {
                    hasTriggered = true
                    onRewarded()
                }
            }
            override fun onAdClosed(adInfo: AdInfo?) {}
            override fun onAdAvailable(adInfo: AdInfo?) {
                if (!hasTriggered) {
                    if (unityRewardedId.isNotEmpty()) {
                        IronSource.showRewardedVideo(unityRewardedId)
                    } else {
                        IronSource.showRewardedVideo()
                    }
                }
            }
            override fun onAdUnavailable() {
                if (!hasTriggered) {
                    hasTriggered = true
                    loadAndShowStartIo(activity, onRewarded, onFailed)
                }
            }
        })

        if (IronSource.isRewardedVideoAvailable()) {
            if (unityRewardedId.isNotEmpty()) {
                IronSource.showRewardedVideo(unityRewardedId)
            } else {
                IronSource.showRewardedVideo()
            }
        } else {
            IronSource.loadRewardedVideo()
        }
    }

    private fun loadAndShowStartIo(
        activity: Activity,
        onRewarded: () -> Unit,
        onFailed: (String) -> Unit
    ) {
        if (startioAppId.isEmpty()) {
            onFailed("Start.io App ID is empty")
            return
        }
        val startAppAd = StartAppAd(activity)
        startAppAd.setVideoListener {
            onRewarded()
        }
        startAppAd.loadAd(StartAppAd.AdMode.REWARDED_VIDEO, object : AdEventListener {
            override fun onReceiveAd(ad: com.startapp.sdk.adsbase.Ad) {
                startAppAd.showAd()
            }
            override fun onFailedToReceiveAd(ad: com.startapp.sdk.adsbase.Ad?) {
                onFailed(ad?.errorMessage ?: "Start.io failed to load")
            }
        })
    }

    var contentAdCounter = 0
    fun showAlternatingAd(context: Context, onAdFinished: () -> Unit) {
        if (isUserAdFree) {
            onAdFinished()
            return
        }
        contentAdCounter++
        if (contentAdCounter % 2 == 0) {
            showRewardedAd(
                context = context,
                onRewarded = { onAdFinished() },
                onFailed = { 
                    showInterstitialAd(context, onClosed = { onAdFinished() })
                }
            )
        } else {
            showInterstitialAd(context, onClosed = { onAdFinished() })
        }
    }

    fun showInterstitialAd(context: Context, onClosed: () -> Unit) {
        if (!isInterstitialEnabled || isUserAdFree) {
            onClosed()
            return
        }
        val activity = context as? Activity
        if (activity == null) {
            onClosed()
            return
        }
        val wrappedOnClosed = {
            onClosed()
            checkAndTriggerUpsellPopup()
        }

        if (unityAppKey.isNotEmpty()) {
            initUnityLevelPlay(activity, unityAppKey)
        }

        when (currentNetwork) {
            AdNetwork.STARTIO -> loadAndShowStartIoInterstitial(activity, wrappedOnClosed)
            AdNetwork.UNITY, AdNetwork.BOTH -> {
                loadAndShowUnityInterstitial(activity, onClosed = wrappedOnClosed, onFailed = {
                    loadAndShowStartIoInterstitial(activity, wrappedOnClosed)
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

        IronSource.setLevelPlayInterstitialListener(object : LevelPlayInterstitialListener {
            override fun onAdReady(adInfo: AdInfo?) {
                if (unityInterstitialId.isNotEmpty()) {
                    IronSource.showInterstitial(unityInterstitialId)
                } else {
                    IronSource.showInterstitial()
                }
            }
            override fun onAdLoadFailed(error: IronSourceError?) {
                onFailed?.invoke() ?: onClosed()
            }
            override fun onAdOpened(adInfo: AdInfo?) {}
            override fun onAdShowSucceeded(adInfo: AdInfo?) {}
            override fun onAdShowFailed(error: IronSourceError?, adInfo: AdInfo?) {
                onFailed?.invoke() ?: onClosed()
            }
            override fun onAdClicked(adInfo: AdInfo?) {}
            override fun onAdClosed(adInfo: AdInfo?) {
                onClosed()
            }
        })
        IronSource.loadInterstitial()
    }

    private fun loadAndShowStartIoInterstitial(
        activity: Activity,
        onClosed: () -> Unit
    ) {
        val startAppAd = StartAppAd(activity)
        startAppAd.loadAd(StartAppAd.AdMode.AUTOMATIC, object : AdEventListener {
            override fun onReceiveAd(ad: com.startapp.sdk.adsbase.Ad) {
                startAppAd.showAd(object : com.startapp.sdk.adsbase.adlisteners.AdDisplayListener {
                    override fun adHidden(p0: com.startapp.sdk.adsbase.Ad?) { onClosed() }
                    override fun adDisplayed(p0: com.startapp.sdk.adsbase.Ad?) {}
                    override fun adClicked(p0: com.startapp.sdk.adsbase.Ad?) {}
                    override fun adNotDisplayed(p0: com.startapp.sdk.adsbase.Ad?) { onClosed() }
                })
            }
            override fun onFailedToReceiveAd(ad: com.startapp.sdk.adsbase.Ad?) {
                onClosed()
            }
        })
    }

    fun getBannerAdView(context: Context): View? {
        if (!isBannerEnabled || isUserAdFree) return null
        val activity = context as? Activity
        if (activity != null && unityAppKey.isNotEmpty()) {
            initUnityLevelPlay(activity, unityAppKey)
        }

        return when (currentNetwork) {
            AdNetwork.UNITY, AdNetwork.BOTH -> {
                if (activity != null && unityAppKey.isNotEmpty()) {
                    val banner = IronSource.createBanner(activity, ISBannerSize.BANNER)
                    if (unityBannerId.isNotEmpty()) {
                        IronSource.loadBanner(banner, unityBannerId)
                    } else {
                        IronSource.loadBanner(banner)
                    }
                    banner
                } else {
                    com.startapp.sdk.ads.banner.Banner(context)
                }
            }
            AdNetwork.STARTIO -> {
                com.startapp.sdk.ads.banner.Banner(context)
            }
        }
    }

    fun getNativeAdView(context: Context): View? {
        if (!isNativeEnabled || isUserAdFree) return null
        return com.startapp.sdk.ads.banner.Mrec(context)
    }

    fun showAppOpenAd(context: Context, onClosed: () -> Unit) {
        if (!isAppOpenEnabled || isUserAdFree) {
            onClosed()
            return
        }
        val activity = context as? Activity
        if (activity == null) {
            onClosed()
            return
        }
        
        when (currentNetwork) {
            AdNetwork.STARTIO -> {
                val startAppAd = StartAppAd(context)
                startAppAd.loadAd(object : AdEventListener {
                    override fun onReceiveAd(ad: com.startapp.sdk.adsbase.Ad) {
                        startAppAd.showAd()
                        onClosed() 
                    }
                    override fun onFailedToReceiveAd(ad: com.startapp.sdk.adsbase.Ad?) {
                        onClosed()
                    }
                })
            }
            AdNetwork.UNITY, AdNetwork.BOTH -> {
                showInterstitialAd(context, onClosed)
            }
        }
    }
}
