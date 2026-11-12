package com.nexiplay.app.ui.components

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nexiplay.app.data.util.AdManager
import kotlinx.coroutines.delay

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DirectLinkOverlayDialog() {
    val session by AdManager.directLinkOverlay.collectAsState()
    val current = session ?: return

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var remainingSeconds by remember(current.id) { mutableIntStateOf(current.durationSeconds.coerceAtLeast(1)) }
    var isForeground by remember(current.id) { mutableStateOf(true) }
    var pageProgress by remember(current.id) { mutableIntStateOf(0) }
    var isLoadingPage by remember(current.id) { mutableStateOf(true) }
    var loadEpoch by remember(current.id) { mutableIntStateOf(0) }
    var isPageLoaded by remember(current.id) { mutableStateOf(false) }
    var webViewRef by remember(current.id) { mutableStateOf<WebView?>(null) }

    val totalSeconds = current.durationSeconds.coerceAtLeast(1)
    val isReady = isPageLoaded && remainingSeconds <= 0

    // Pause timer if the user leaves or minimizes the app so verification stays genuine
    DisposableEffect(lifecycleOwner, current.id) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> isForeground = true
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> isForeground = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Unlock the countdown only AFTER the Direct Link page + redirect chain has loaded
    LaunchedEffect(current.id, loadEpoch, isLoadingPage, pageProgress) {
        if (isPageLoaded) return@LaunchedEffect
        if (loadEpoch > 0 && (!isLoadingPage || pageProgress >= 95)) {
            delay(850L) // allow immediate Adsterra JS/HTTP redirect hops to start before locking in loaded state
            if (!isLoadingPage || pageProgress >= 95) {
                isPageLoaded = true
            }
        }
    }

    // Safety fallback cap (15s foreground wait max) so a stalled third-party analytics script never locks users forever
    LaunchedEffect(current.id, isForeground) {
        var foregroundWaitMs = 0L
        while (!isPageLoaded && foregroundWaitMs < 15_000L) {
            if (isForeground) {
                delay(250L)
                foregroundWaitMs += 250L
            } else {
                delay(250L)
            }
        }
        if (!isPageLoaded && foregroundWaitMs >= 15_000L) {
            isPageLoaded = true
        }
    }

    // Countdown runs strictly AFTER the page has loaded and only while app is in foreground
    LaunchedEffect(current.id, isPageLoaded, isForeground) {
        if (!isPageLoaded) return@LaunchedEffect
        while (remainingSeconds > 0) {
            if (isForeground) {
                delay(1000L)
                if (isForeground && remainingSeconds > 0) {
                    remainingSeconds--
                }
            } else {
                delay(250L)
            }
        }
    }

    val animatedTimerProgress by animateFloatAsState(
        targetValue = if (!isPageLoaded) {
            0f
        } else {
            ((totalSeconds - remainingSeconds).toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f)
        },
        label = "directLinkTimerProgress"
    )

    Dialog(
        onDismissRequest = { AdManager.dismissDirectLinkOverlay(completed = false) },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0B0F19))
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top Overlay Countdown Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF111827))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Countdown Pill
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (isReady) Color(0xFF10B981).copy(alpha = 0.18f)
                                else Color(0xFF8B5CF6).copy(alpha = 0.18f)
                            )
                            .border(
                                1.dp,
                                if (isReady) Color(0xFF10B981).copy(alpha = 0.55f)
                                else Color(0xFF8B5CF6).copy(alpha = 0.45f),
                                RoundedCornerShape(20.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = if (isReady) Icons.Rounded.CheckCircle else Icons.Rounded.Timer,
                            contentDescription = null,
                            tint = when {
                                isReady -> Color(0xFF34D399)
                                !isPageLoaded -> Color(0xFFFBBF24)
                                else -> Color(0xFFC4B5FD)
                            },
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = when {
                                isReady -> "0s"
                                !isPageLoaded -> "Wait..."
                                else -> "${remainingSeconds}s"
                            },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isReady) Color(0xFF34D399) else Color.White
                        )
                    }

                    // Status Title & Helper Text
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = current.title,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = when {
                                isReady -> "Verified! Tap ${current.actionLabel} below"
                                !isPageLoaded -> "Loading sponsor page... timer starts once loaded (${pageProgress}%)"
                                else -> "Page loaded • Wait ${remainingSeconds}s to continue"
                            },
                            fontSize = 11.sp,
                            color = if (isReady) Color(0xFF34D399) else Color(0xFF9CA3AF),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Open in external browser option (timer still stays tied to verification)
                    IconButton(
                        onClick = {
                            try {
                                val target = webViewRef?.url?.takeIf {
                                    it.startsWith("http://") || it.startsWith("https://")
                                } ?: current.url
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)))
                            } catch (_: Exception) {}
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.OpenInBrowser,
                            contentDescription = "Open in browser",
                            tint = Color(0xFF9CA3AF),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    if (isReady) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFF10B981), Color(0xFF059669))
                                    )
                                )
                                .clickable {
                                    AdManager.dismissDirectLinkOverlay(completed = true)
                                }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = current.actionLabel,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.08f))
                                .clickable {
                                    AdManager.dismissDirectLinkOverlay(completed = false)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Close",
                                tint = Color(0xFFD1D5DB),
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }

                // Live Countdown Progress Bar
                LinearProgressIndicator(
                    progress = { animatedTimerProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = if (isReady) Color(0xFF10B981) else Color(0xFF8B5CF6),
                    trackColor = Color.White.copy(alpha = 0.1f)
                )

                // Subtle page load progress while loading initial redirect
                if (pageProgress in 1..99) {
                    LinearProgressIndicator(
                        progress = { pageProgress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                        color = Color(0xFF38BDF8),
                        trackColor = Color.Transparent
                    )
                }
            }

            // Embedded WebView showing the live Direct Link page
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.White)
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            val cm = CookieManager.getInstance()
                            cm.setAcceptCookie(true)
                            cm.setAcceptThirdPartyCookies(this, true)

                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                loadsImagesAutomatically = true
                                javaScriptCanOpenWindowsAutomatically = false
                                setSupportMultipleWindows(false)
                                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                userAgentString =
                                    "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                            }

                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    pageProgress = newProgress
                                    if (newProgress >= 100 && loadEpoch > 0) {
                                        isLoadingPage = false
                                    }
                                }
                            }

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(
                                    view: WebView?,
                                    url: String?,
                                    favicon: android.graphics.Bitmap?
                                ) {
                                    super.onPageStarted(view, url, favicon)
                                    if (!url.isNullOrBlank() && url != "about:blank") {
                                        isLoadingPage = true
                                        loadEpoch++
                                    }
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    if (!url.isNullOrBlank() && url != "about:blank") {
                                        isLoadingPage = false
                                        if (pageProgress < 100) {
                                            pageProgress = 100
                                        }
                                    }
                                }

                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    val nextUrl = request?.url?.toString().orEmpty()
                                    if (nextUrl.startsWith("http://", ignoreCase = true) ||
                                        nextUrl.startsWith("https://", ignoreCase = true)
                                    ) {
                                        isLoadingPage = true
                                        return false // keep inside overlay WebView
                                    }
                                    return try {
                                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(nextUrl)))
                                        true
                                    } catch (_: Exception) {
                                        true
                                    }
                                }
                            }

                            webViewRef = this
                            loadUrl(current.url)
                        }
                    },
                    onRelease = { wv ->
                        webViewRef = null
                        try {
                            wv.stopLoading()
                            wv.destroy()
                        } catch (_: Exception) {}
                    }
                )
            }

            // Prominent bottom CTA bar when countdown finishes so every user spots Watch / Download / Read immediately
            AnimatedVisibility(
                visible = isReady,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 })
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F172A))
                        .border(1.dp, Color(0xFF10B981).copy(alpha = 0.35f))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF34D399),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Verification complete! Tap below to continue",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF34D399)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFF10B981), Color(0xFF059669))
                                )
                            )
                            .clickable {
                                AdManager.dismissDirectLinkOverlay(completed = true)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = current.actionLabel,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}
