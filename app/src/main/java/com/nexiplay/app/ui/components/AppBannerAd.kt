package com.nexiplay.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.viewinterop.AndroidView
import com.nexiplay.app.data.util.AdManager
import android.view.View

@Composable
fun AppBannerAd() {
    AndroidView(factory = { context -> 
        AdManager.getBannerAdView(context) ?: View(context) 
    })
}
