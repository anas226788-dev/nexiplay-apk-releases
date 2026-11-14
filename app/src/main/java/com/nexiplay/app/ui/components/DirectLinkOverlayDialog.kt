package com.nexiplay.app.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.net.http.SslError
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nexiplay.app.data.util.AdManager
import com.nexiplay.app.data.util.DirectLinkHelper
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
    var mainFrameError by remember(current.id) { mutableStateOf<String?>(null) }
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

    // Safety fallback cap (12s foreground wait max) so a stalled third-party script never locks users forever
    LaunchedEffect(current.id, isForeground) {
        var foregroundWaitMs = 0L
        while (!isPageLoaded && foregroundWaitMs < 12_000L) {
            if (isForeground) {
                delay(250L)
                foregroundWaitMs += 250L
            } else {
                delay(250L)
            }
        }
        if (!isPageLoaded && foregroundWaitMs >= 12_000L) {
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
            decorFitsSystemWindows = true
        )
    ) {
        val dialogView = LocalView.current
        val density = LocalDensity.current
        val dialogWindow = (dialogView.parent as? DialogWindowProvider)?.window

        val initialVisibleHeightPx = remember(current.id) {
            val rect = Rect()
            val decor = (context as? android.app.Activity)?.window?.decorView
            if (decor != null) {
                decor.getWindowVisibleDisplayFrame(rect)
                (rect.bottom - rect.top).takeIf { it > 300 } ?: 0
            } else {
                0
            }
        }
        var containerHeightPx by remember(current.id) { mutableIntStateOf(initialVisibleHeightPx) }
        var topInsetPx by remember(current.id) { mutableIntStateOf(0) }

        DisposableEffect(dialogView, dialogWindow, current.id) {
            val decor = dialogWindow?.decorView ?: (dialogView.rootView ?: dialogView)
            val updateVisibleBounds = {
                val visibleRect = Rect()
                decor.getWindowVisibleDisplayFrame(visibleRect)
                val loc = IntArray(2)
                dialogView.getLocationOnScreen(loc)
                val maxHeight = visibleRect.bottom - loc[1]
                val neededTop = (visibleRect.top - loc[1]).coerceAtLeast(0)
                if (dialogView.isAttachedToWindow && visibleRect.bottom > visibleRect.top && maxHeight > 300) {
                    containerHeightPx = maxHeight
                    topInsetPx = neededTop
                }
            }
            val listener = ViewTreeObserver.OnGlobalLayoutListener { updateVisibleBounds() }
            decor.viewTreeObserver.addOnGlobalLayoutListener(listener)
            dialogView.post { updateVisibleBounds() }
            onDispose {
                decor.viewTreeObserver.removeOnGlobalLayoutListener(listener)
            }
        }

        SideEffect {
            dialogWindow?.let { window ->
                window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                window.setBackgroundDrawable(ColorDrawable(0xFF0B0F19.toInt()))
                window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            }
        }

        val rootModifier = if (containerHeightPx > 0) {
            Modifier
                .fillMaxWidth()
                .height(with(density) { containerHeightPx.toDp() })
        } else {
            Modifier.fillMaxSize()
        }

        Column(
            modifier = rootModifier
                .background(Color(0xFF0B0F19))
                .padding(top = with(density) { topInsetPx.toDp() })
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
                                !isPageLoaded -> "Loading verification page... (${pageProgress}%)"
                                else -> "Page loaded • Wait ${remainingSeconds}s to continue"
                            },
                            fontSize = 11.sp,
                            color = if (isReady) Color(0xFF34D399) else Color(0xFF9CA3AF),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Open in external browser option
                    IconButton(
                        onClick = {
                            val target = webViewRef?.url?.let { DirectLinkHelper.normalizeHttpUrl(it) } ?: current.url
                            AdManager.openExternalDirectLink(context, target)
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
                    .background(Color(0xFF0F172A))
            ) {
                var pendingPopupView by remember(current.id) { mutableStateOf<WebView?>(null) }

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
                                javaScriptCanOpenWindowsAutomatically = true
                                setSupportMultipleWindows(true)
                                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                userAgentString =
                                    "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
                            }

                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    pageProgress = newProgress
                                    if (newProgress >= 100 && loadEpoch > 0) {
                                        isLoadingPage = false
                                    }
                                }

                                override fun onCreateWindow(
                                    view: WebView?,
                                    isDialog: Boolean,
                                    isUserGesture: Boolean,
                                    resultMsg: Message?
                                ): Boolean {
                                    val mainWebView = view ?: return false
                                    val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false

                                    // Fast path if anchor href is already known via HitTestResult
                                    val hitExtra = runCatching { mainWebView.hitTestResult.extra }.getOrNull()
                                    if (!hitExtra.isNullOrBlank()) {
                                        when (DirectLinkHelper.classifyOverlayUrl(hitExtra)) {
                                            DirectLinkHelper.OverlayUrlAction.LOAD_IN_WEBVIEW -> {
                                                mainFrameError = null
                                                isLoadingPage = true
                                                mainWebView.loadUrl(hitExtra)
                                                return false
                                            }
                                            DirectLinkHelper.OverlayUrlAction.OPEN_EXTERNAL -> {
                                                handleExternalOverlayUrl(ctx, hitExtra, mainWebView)
                                                return false
                                            }
                                            DirectLinkHelper.OverlayUrlAction.IGNORE -> Unit
                                        }
                                    }

                                    // Ephemeral popup interceptor routes window.open / target="_blank" safely
                                    pendingPopupView?.let { old ->
                                        runCatching {
                                            old.stopLoading()
                                            old.destroy()
                                        }
                                    }
                                    val popupInterceptor = WebView(ctx)
                                    popupInterceptor.settings.javaScriptEnabled = true
                                    popupInterceptor.webViewClient = object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(
                                            pView: WebView?,
                                            req: WebResourceRequest?
                                        ): Boolean {
                                            val targetUrl = req?.url?.toString().orEmpty()
                                            routePopupUrl(ctx, targetUrl, mainWebView)
                                            cleanupPopupView(popupInterceptor)
                                            return true
                                        }

                                        override fun onPageStarted(
                                            pView: WebView?,
                                            url: String?,
                                            favicon: android.graphics.Bitmap?
                                        ) {
                                            if (!url.isNullOrBlank() && url != "about:blank") {
                                                routePopupUrl(ctx, url, mainWebView)
                                                cleanupPopupView(popupInterceptor)
                                            }
                                        }

                                        private fun routePopupUrl(
                                            context: Context,
                                            url: String,
                                            hostWebView: WebView
                                        ) {
                                            when (DirectLinkHelper.classifyOverlayUrl(url)) {
                                                DirectLinkHelper.OverlayUrlAction.LOAD_IN_WEBVIEW -> {
                                                    mainFrameError = null
                                                    isLoadingPage = true
                                                    hostWebView.loadUrl(url)
                                                }
                                                DirectLinkHelper.OverlayUrlAction.OPEN_EXTERNAL -> {
                                                    handleExternalOverlayUrl(context, url, hostWebView)
                                                }
                                                DirectLinkHelper.OverlayUrlAction.IGNORE -> Unit
                                            }
                                        }

                                        private fun cleanupPopupView(target: WebView) {
                                            target.post {
                                                runCatching {
                                                    target.stopLoading()
                                                    target.destroy()
                                                }
                                                if (pendingPopupView === target) {
                                                    pendingPopupView = null
                                                }
                                            }
                                        }
                                    }
                                    pendingPopupView = popupInterceptor
                                    transport.webView = popupInterceptor
                                    resultMsg.sendToTarget()
                                    return true
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
                                        mainFrameError = null
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

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?
                                ) {
                                    super.onReceivedError(view, request, error)
                                    if (request?.isForMainFrame == true) {
                                        mainFrameError = error?.description?.toString()
                                            ?.takeIf { it.isNotBlank() }
                                            ?: "Could not reach verification page"
                                        isLoadingPage = false
                                        if (loadEpoch == 0) loadEpoch = 1
                                        isPageLoaded = true
                                    }
                                }

                                override fun onReceivedSslError(
                                    view: WebView?,
                                    handler: SslErrorHandler?,
                                    error: SslError?
                                ) {
                                    handler?.cancel()
                                    mainFrameError = "Secure connection unavailable"
                                    isLoadingPage = false
                                    if (loadEpoch == 0) loadEpoch = 1
                                    isPageLoaded = true
                                }

                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    val nextUrl = request?.url?.toString().orEmpty()
                                    return when (DirectLinkHelper.classifyOverlayUrl(nextUrl)) {
                                        DirectLinkHelper.OverlayUrlAction.IGNORE -> true
                                        DirectLinkHelper.OverlayUrlAction.LOAD_IN_WEBVIEW -> {
                                            mainFrameError = null
                                            isLoadingPage = true
                                            false // keep inside overlay WebView
                                        }
                                        DirectLinkHelper.OverlayUrlAction.OPEN_EXTERNAL -> {
                                            handleExternalOverlayUrl(ctx, nextUrl, view)
                                            true
                                        }
                                    }
                                }
                            }

                            webViewRef = this
                            loadUrl(current.url)
                        }
                    },
                    onRelease = { wv ->
                        pendingPopupView?.let { pv ->
                            runCatching {
                                pv.stopLoading()
                                pv.destroy()
                            }
                            pendingPopupView = null
                        }
                        webViewRef = null
                        runCatching {
                            wv.stopLoading()
                            wv.destroy()
                        }
                    }
                )

                // Non-blocking error recovery overlay if DNS/ad-blocker/network blocks the sponsor URL
                if (mainFrameError != null) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(12.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xE6111827))
                            .border(1.dp, Color(0x4DF59E0B), RoundedCornerShape(14.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Verification page blocked or slow on this network",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFBBF24)
                            )
                            Text(
                                text = "Wait for timer below or open link in browser",
                                fontSize = 11.sp,
                                color = Color(0xFFD1D5DB),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.1f))
                                .clickable {
                                    mainFrameError = null
                                    isLoadingPage = true
                                    webViewRef?.loadUrl(current.url)
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = "Retry",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text("Retry", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                            }
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF2563EB))
                                .clickable {
                                    AdManager.openExternalDirectLink(context, current.url)
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text("Open", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }

            // Always-reserved bottom footer slot prevents vertical jump/clipping under edge-to-edge navigation bars
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 86.dp)
                    .background(Color(0xFF0F172A))
                    .border(
                        1.dp,
                        if (isReady) Color(0xFF10B981).copy(alpha = 0.4f) else Color.White.copy(alpha = 0.08f)
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (isReady) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF34D399),
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "Verification complete! Tap below to continue",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF34D399),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(12.dp))
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
                            val ctaIcon = when {
                                current.actionLabel.contains("download", ignoreCase = true) -> Icons.Rounded.CloudDownload
                                current.actionLabel.contains("claim", ignoreCase = true) -> Icons.Rounded.CheckCircle
                                else -> Icons.Rounded.PlayArrow
                            }
                            Icon(
                                imageVector = ctaIcon,
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
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Timer,
                            contentDescription = null,
                            tint = Color(0xFFC4B5FD),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (!isPageLoaded) {
                                "Verifying access... ${current.actionLabel} unlocks shortly"
                            } else {
                                "Please wait ${remainingSeconds}s to unlock ${current.actionLabel}"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFD1D5DB),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

private fun handleExternalOverlayUrl(
    ctx: Context,
    nextUrl: String,
    webView: WebView?
) {
    if (nextUrl.startsWith("intent://", ignoreCase = true)) {
        val parsed = runCatching { Intent.parseUri(nextUrl, Intent.URI_INTENT_SCHEME) }.getOrNull()
        if (parsed != null) {
            if (ctx !is android.app.Activity) {
                parsed.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val canLaunch = runCatching { parsed.resolveActivity(ctx.packageManager) != null }.getOrDefault(false)
            if (canLaunch && runCatching { ctx.startActivity(parsed) }.isSuccess) {
                return
            }
            val fallback = DirectLinkHelper.normalizeHttpUrl(parsed.getStringExtra("browser_fallback_url"))
                ?: DirectLinkHelper.extractBrowserFallbackUrlFromIntentUri(nextUrl)
            if (fallback != null && webView != null) {
                webView.post { webView.loadUrl(fallback) }
                return
            }
        }
        val uriFallback = DirectLinkHelper.extractBrowserFallbackUrlFromIntentUri(nextUrl)
        if (uriFallback != null && webView != null) {
            webView.post { webView.loadUrl(uriFallback) }
        }
        return
    }

    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(nextUrl)).apply {
            if (ctx !is android.app.Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        ctx.startActivity(intent)
    }
}
