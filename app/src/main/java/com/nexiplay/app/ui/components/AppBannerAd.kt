package com.nexiplay.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.viewinterop.AndroidView
import com.ironsource.mediationsdk.IronSource
import com.ironsource.mediationsdk.IronSourceBannerLayout
import com.nexiplay.app.data.util.AdManager
import android.view.View

@Composable
fun AppBannerAd() {
    val isPremium by AdManager.premiumState.collectAsState()
    if (isPremium || !AdManager.isBannerEnabled) return

    AndroidView(
        factory = { context ->
            AdManager.getBannerAdView(context) ?: View(context)
        },
        onRelease = { view ->
            if (view is IronSourceBannerLayout) {
                try {
                    IronSource.destroyBanner(view)
                } catch (_: Exception) {}
            }
        }
    )
}
