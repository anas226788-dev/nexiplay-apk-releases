package com.nexiplay.app.ui.components

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.ironsource.mediationsdk.IronSource
import com.ironsource.mediationsdk.IronSourceBannerLayout
import com.nexiplay.app.data.util.AdManager
import com.startapp.sdk.ads.banner.bannerstandard.BannerStandard

@Composable
fun AppBannerAd(modifier: Modifier = Modifier) {
    val isPremium by AdManager.premiumState.collectAsState()
    val configLoaded by AdManager.configLoadedFlow.collectAsState()
    if (!configLoaded || isPremium || !AdManager.isBannerEnabled) return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            factory = { context ->
                AdManager.getBannerAdView(context) ?: View(context).apply { visibility = View.GONE }
            },
            onRelease = { view ->
                releaseBannerView(view)
            }
        )
    }
}

private fun releaseBannerView(view: View) {
    if (view is IronSourceBannerLayout) {
        try {
            IronSource.destroyBanner(view)
        } catch (_: Exception) {}
    } else if (view is BannerStandard) {
        try {
            view.hideBanner()
        } catch (_: Exception) {}
    } else if (view is ViewGroup) {
        for (i in 0 until view.childCount) {
            releaseBannerView(view.getChildAt(i))
        }
        if (view is FrameLayout) {
            view.removeAllViews()
        }
    }
}
