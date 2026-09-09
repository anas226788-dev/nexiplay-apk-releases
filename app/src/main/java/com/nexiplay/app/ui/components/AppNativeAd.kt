package com.nexiplay.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.nexiplay.app.data.util.AdManager
import android.view.View

@Composable
fun AppNativeAd() {
    val isPremium = AdManager.premiumState.collectAsState().value
    if (isPremium) return

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(factory = { context -> 
            AdManager.getNativeAdView(context) ?: View(context) 
        })
    }
}
