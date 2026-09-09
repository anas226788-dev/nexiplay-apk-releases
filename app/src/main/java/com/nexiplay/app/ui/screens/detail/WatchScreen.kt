package com.nexiplay.app.ui.screens.detail

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.nexiplay.app.data.model.Episode
import com.nexiplay.app.ui.components.ShimmerBox
import com.nexiplay.app.ui.components.bounceClick
import com.nexiplay.app.ui.theme.InterFont
import com.nexiplay.app.ui.theme.NexiRed
import com.nexiplay.app.data.model.EventMetadata
import com.nexiplay.app.data.model.UserEvent
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import com.nexiplay.app.data.SupabaseClient
import java.util.UUID
import com.nexiplay.app.ui.theme.themeBg
import kotlinx.coroutines.launch
import kotlinx.coroutines.GlobalScope
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.nexiplay.app.ui.theme.themeCard
import com.nexiplay.app.ui.theme.themeTextPrimary
import com.nexiplay.app.ui.theme.themeTextSecondary
import com.nexiplay.app.ui.theme.themeTextTertiary
import com.nexiplay.app.util.findActivity
import java.io.ByteArrayInputStream

private val AD_HOST_KEYWORDS = listOf(
    "doubleclick", "googlesyndication", "googleadservices", "google-analytics",
    "googletagmanager", "googletagservices", "adservice",
    "adserv", "adtrack", "advert", "adsystem", "adserver", "adnetwork",
    "popunder", "popcash", "popads", "propeller",
    "trafficjunky", "exoclick", "juicyads", "clickadu", "hilltopads",
    "monetag", "adsterra", "pushame", "pushengage", "richpush",
    "taboola", "outbrain", "mgid", "revcontent"
)

private val AD_PATH_KEYWORDS = listOf(
    "/ads/", "/ad/", "/adserv", "/banner/", "/popup",
    "/pagead/", "/aclk?", "google_ads", "amazon_ads", "prebid", "adsense"
)

private fun isAdUrl(url: String): Boolean {
    val lower = url.lowercase()
    val host = try { java.net.URI(lower).host ?: "" } catch (e: Exception) { lower }
    for (keyword in AD_HOST_KEYWORDS) { if (host.contains(keyword)) return true }
    for (keyword in AD_PATH_KEYWORDS) { if (lower.contains(keyword)) return true }
    return false
}

private val EMPTY_RESPONSE = WebResourceResponse(
    "text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0))
)

private const val AD_HIDE_JS = """
(function() {
    var style = document.createElement('style');
    style.textContent = `
        [class*="ad-"], [class*="ads-"], [class*="adsbygoogle"],
        [class*="ad_"], [class*="ads_"], [class*="advert"],
        [id*="ad-"], [id*="ads-"], [id*="google_ads"], [id*="ad_"],
        iframe[src*="ads"], iframe[src*="doubleclick"],
        iframe[src*="googlesyndication"], iframe[src*="adserv"],
        iframe[src*="exoclick"], iframe[src*="monetag"],
        ins.adsbygoogle, .adsbygoogle,
        .ad-container, .ad-wrapper, .ad-banner, .ad-unit, .ad-slot,
        .ad-overlay, .ad-popup, .ad-interstitial,
        [data-ad], [data-ads], [data-ad-slot],
        div[class*="push-notification"], div[id*="push-notification"]
        { display: none !important; height: 0 !important; width: 0 !important; overflow: hidden !important; }
        body { overflow: auto !important; }
    `;
    document.head.appendChild(style);
    document.querySelectorAll('iframe').forEach(function(el) {
        var src = (el.src || '').toLowerCase();
        if (src.includes('ad') || src.includes('doubleclick') || src.includes('syndication') ||
            src.includes('exoclick') || src.includes('monetag') || src.includes('propeller')) {
            el.remove();
        }
    });
    window.open = function() { return null; };
})();
"""

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchScreen(
    navController: NavController,
    type: String,
    slug: String,
    vm: DetailViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val activity = context.findActivity()

    var selectedServerUrl by remember { mutableStateOf<String?>(null) }
    var selectedServerId by remember { mutableStateOf<String?>(null) }
    var selectedSeasonIdx by remember { mutableIntStateOf(0) }
    var selectedEpisode by remember { mutableStateOf<Episode?>(null) }
    var isPremiumUnlocked by remember { mutableStateOf(false) }
    
    // Fullscreen state
    var isFullscreen by remember { mutableStateOf(false) }
    var customView by remember { mutableStateOf<android.view.View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    LaunchedEffect(type, slug) {
        vm.loadContent(type, slug)
    }

    // Auto-select first episode for series
    LaunchedEffect(state.seasons, state.episodes) {
        if ((type == "series" || type == "anime") && state.seasons.isNotEmpty() && selectedEpisode == null) {
            val firstSeason = state.seasons.first()
            val episodes = state.episodes[firstSeason.id]
            if (!episodes.isNullOrEmpty()) {
                selectedEpisode = episodes.first()
            }
        }
    }

    // Auto-select top server when content/episodes load
    LaunchedEffect(state.movie, selectedEpisode, state.streamingRow) {
        if (state.movie != null && selectedServerUrl == null) {
            val isSeries = type == "series" || type == "anime"
            val currentServers = getAvailableServers(
                type = type,
                streamingRow = state.streamingRow,
                movie = state.movie!!,
                episode = selectedEpisode,
                seasonNumber = if (isSeries) state.seasons.getOrNull(selectedSeasonIdx)?.seasonNumber ?: 1 else 1
            )
            val firstServer = currentServers.firstOrNull()
            if (firstServer != null) {
                selectedServerId = firstServer.id
                selectedServerUrl = firstServer.url
            }
        }
    }

    // Helper: Play Next Episode
    val playNextEpisode: () -> Unit = {
        val isSeries = type == "series" || type == "anime"
        if (isSeries && state.seasons.isNotEmpty() && state.movie != null) {
            val currentSeason = state.seasons.getOrNull(selectedSeasonIdx)
            val episodes = currentSeason?.let { state.episodes[it.id] } ?: emptyList()
            val currentEpIdx = episodes.indexOfFirst { it.id == selectedEpisode?.id }

            val nextEp: Episode?
            val nextSeasonIdx: Int

            if (currentEpIdx != -1 && currentEpIdx + 1 < episodes.size) {
                nextEp = episodes[currentEpIdx + 1]
                nextSeasonIdx = selectedSeasonIdx
            } else if (selectedSeasonIdx + 1 < state.seasons.size) {
                nextSeasonIdx = selectedSeasonIdx + 1
                val nextSeason = state.seasons[nextSeasonIdx]
                nextEp = state.episodes[nextSeason.id]?.firstOrNull()
            } else {
                nextEp = null
                nextSeasonIdx = selectedSeasonIdx
            }

            if (nextEp != null) {
                selectedSeasonIdx = nextSeasonIdx
                selectedEpisode = nextEp

                val currentServers = getAvailableServers(
                    type = type,
                    streamingRow = state.streamingRow,
                    movie = state.movie!!,
                    episode = nextEp,
                    seasonNumber = state.seasons.getOrNull(nextSeasonIdx)?.seasonNumber ?: 1
                )
                val matchedServer = currentServers.find { it.id == selectedServerId } ?: currentServers.firstOrNull()
                if (matchedServer != null) {
                    selectedServerId = matchedServer.id
                    if (matchedServer.id == "toonplay" && type != "movie" && matchedServer.url.startsWith("series-")) {
                        selectedServerUrl = null
                        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            val result = com.nexiplay.app.data.util.ToonplayResolver.resolve(
                                toonplayId = matchedServer.url,
                                season = state.seasons.getOrNull(nextSeasonIdx)?.seasonNumber ?: 1,
                                episode = nextEp.episodeNumber
                            )
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                if (result.url.isNotEmpty() && result.source != "error") {
                                    val encoded = java.net.URLEncoder.encode(result.url, "UTF-8")
                                    navController.navigate("player/$encoded")
                                } else {
                                    selectedServerUrl = matchedServer.url
                                }
                            }
                        }
                    } else if (matchedServer.url.lowercase().run { contains(".m3u8") || contains(".mp4") || contains(".mkv") }) {
                        val encoded = java.net.URLEncoder.encode(matchedServer.url, "UTF-8")
                        navController.navigate("player/$encoded")
                    } else {
                        selectedServerUrl = matchedServer.url
                    }
                }
            }
        }
    }

    // Auto-Next Episode Signal Listener
    val currentBackStackEntry = navController.currentBackStackEntry
    val autoNextState = remember(currentBackStackEntry) {
        currentBackStackEntry?.savedStateHandle?.getStateFlow("auto_next_episode", false)
    }
    val autoNextSignal by autoNextState?.collectAsState() ?: remember { mutableStateOf(false) }

    LaunchedEffect(autoNextSignal) {
        if (autoNextSignal) {
            currentBackStackEntry?.savedStateHandle?.remove<Boolean>("auto_next_episode")
            playNextEpisode()
        }
    }

    // Track Watch History
    var eventId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.movie, selectedEpisode) {
        state.movie?.let { movie ->
            val user = SupabaseClient.main.auth.currentUserOrNull()
            if (user != null) {
                try {
                    val isSeries = (movie.type == "series" || movie.type == "anime")
                    val seasonNum = if (isSeries) state.seasons.getOrNull(selectedSeasonIdx)?.seasonNumber else null
                    val metadata = EventMetadata(
                        title = movie.title,
                        slug = movie.slug,
                        posterUrl = movie.posterUrl,
                        type = movie.type,
                        source = "android_app"
                    )
                    val event = UserEvent(
                        userId = user.id,
                        eventType = "watch",
                        movieId = movie.id,
                        episodeId = selectedEpisode?.id,
                        contentType = movie.type,
                        contentTitle = movie.title,
                        seasonNumber = seasonNum,
                        episodeNumber = selectedEpisode?.episodeNumber,
                        metadata = metadata
                    )
                    val inserted = SupabaseClient.main.from("user_events").insert(event) {
                        select()
                    }.decodeSingle<UserEvent>()
                    eventId = inserted.id

                    // Record watch for daily coin rewards
                    com.nexiplay.app.data.util.CoinRewardHelper.recordEpisodeWatch(context)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    LaunchedEffect(eventId) {
        if (eventId != null) {
            val startTime = System.currentTimeMillis()
            while(true) {
                kotlinx.coroutines.delay(30000)
                val currentDuration = ((System.currentTimeMillis() - startTime) / 1000).toInt()
                try {
                    SupabaseClient.main.from("user_events").update(
                        buildJsonObject { put("duration_seconds", currentDuration) }
                    ) {
                        filter { eq("id", eventId!!) }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    DisposableEffect(eventId) {
        val currentEventId = eventId
        val startTime = System.currentTimeMillis()
        onDispose {
            if (currentEventId != null) {
                val finalDuration = ((System.currentTimeMillis() - startTime) / 1000).toInt()
                kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        SupabaseClient.main.from("user_events").update(
                            buildJsonObject { put("duration_seconds", finalDuration) }
                        ) {
                            filter { eq("id", currentEventId) }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    // Fullscreen handling
    DisposableEffect(isFullscreen) {
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        if (isFullscreen) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            // Hide system UI here if needed
        } else {
            activity?.requestedOrientation = originalOrientation
        }
        onDispose {
            activity?.requestedOrientation = originalOrientation
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        androidx.activity.compose.BackHandler(enabled = isFullscreen) {
            customViewCallback?.onCustomViewHidden()
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    if (state.isLoading) {
                        ShimmerBox(modifier = Modifier.width(120.dp).height(20.dp), cornerRadius = 4)
                    } else if (state.movie != null) {
                        Text(
                            state.movie!!.title, 
                            maxLines = 1, 
                            fontSize = 16.sp, 
                            color = themeTextPrimary(),
                            fontFamily = InterFont
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = themeTextPrimary())
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = themeBg())
            )
        },
        containerColor = themeBg()
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (state.isLoading) {
                Column(Modifier.fillMaxSize()) {
                    ShimmerBox(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f), cornerRadius = 0)
                    Column(Modifier.padding(16.dp)) {
                        ShimmerBox(modifier = Modifier.width(150.dp).height(24.dp), cornerRadius = 4)
                        Spacer(Modifier.height(8.dp))
                        Row {
                            ShimmerBox(modifier = Modifier.width(100.dp).height(40.dp), cornerRadius = 12)
                            Spacer(Modifier.width(8.dp))
                            ShimmerBox(modifier = Modifier.width(100.dp).height(40.dp), cornerRadius = 12)
                        }
                        Spacer(Modifier.height(24.dp))
                        ShimmerBox(modifier = Modifier.width(150.dp).height(24.dp), cornerRadius = 4)
                        Spacer(Modifier.height(8.dp))
                        ShimmerBox(modifier = Modifier.fillMaxWidth().height(60.dp), cornerRadius = 12)
                        Spacer(Modifier.height(8.dp))
                        ShimmerBox(modifier = Modifier.fillMaxWidth().height(60.dp), cornerRadius = 12)
                    }
                }
            } else if (state.error != null || state.movie == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Content not found", 
                        color = themeTextSecondary(), 
                        fontFamily = InterFont
                    )
                }
            }

            AnimatedVisibility(
                visible = !state.isLoading && state.movie != null,
                enter = fadeIn()
            ) {
                if (state.movie != null) {
                    val movie = state.movie!!
                    val isSeries = type == "series" || type == "anime"

                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Player Container
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            if (selectedServerUrl != null) {
                                AndroidView(
                                    factory = { ctx ->
                                        WebView(ctx).apply {
                                            settings.javaScriptEnabled = true
                                            settings.domStorageEnabled = true
                                            settings.mediaPlaybackRequiresUserGesture = false
                                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                            settings.setSupportMultipleWindows(true)
                                            settings.javaScriptCanOpenWindowsAutomatically = true
                                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                            
                                            addJavascriptInterface(object {
                                                @android.webkit.JavascriptInterface
                                                fun onVideoEnded() {
                                                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                                                        playNextEpisode()
                                                    }
                                                }
                                            }, "AndroidBridge")

                                            webViewClient = object : WebViewClient() {
                                                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: android.webkit.WebResourceError?) {
                                                    super.onReceivedError(view, request, error)
                                                    if (request?.isForMainFrame == true) {
                                                        android.widget.Toast.makeText(
                                                            view?.context,
                                                            "Server not responding. Try switching to another server.",
                                                            android.widget.Toast.LENGTH_LONG
                                                        ).show()
                                                    }
                                                }

                                                override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
                                                    super.onReceivedHttpError(view, request, errorResponse)
                                                    if (request?.isForMainFrame == true && (errorResponse?.statusCode ?: 200) >= 400) {
                                                        android.widget.Toast.makeText(
                                                            view?.context,
                                                            "Server error (${errorResponse?.statusCode}). Try another server.",
                                                            android.widget.Toast.LENGTH_LONG
                                                        ).show()
                                                    }
                                                }

                                                override fun shouldInterceptRequest(
                                                    view: WebView?, request: WebResourceRequest?
                                                ): WebResourceResponse? {
                                                    val requestUrl = request?.url?.toString() ?: return null
                                                    if (isAdUrl(requestUrl)) return EMPTY_RESPONSE
                                                    return super.shouldInterceptRequest(view, request)
                                                }

                                                override fun onPageFinished(view: WebView?, url: String?) {
                                                    super.onPageFinished(view, url)
                                                    view?.evaluateJavascript(AD_HIDE_JS, null)
                                                    view?.evaluateJavascript("""
                                                        (function() {
                                                            function attachVideoListeners() {
                                                                var videos = document.querySelectorAll('video');
                                                                videos.forEach(function(v) {
                                                                    if (!v.dataset.hasNextListener) {
                                                                        v.dataset.hasNextListener = 'true';
                                                                        v.addEventListener('ended', function() {
                                                                            if (window.AndroidBridge && window.AndroidBridge.onVideoEnded) {
                                                                                window.AndroidBridge.onVideoEnded();
                                                                            }
                                                                        });
                                                                    }
                                                                });
                                                            }
                                                            attachVideoListeners();
                                                            setInterval(attachVideoListeners, 2000);
                                                        })();
                                                    """.trimIndent(), null)
                                                }
                                            }

                                            webChromeClient = object : WebChromeClient() {
                                                override fun onShowCustomView(view: android.view.View?, callback: CustomViewCallback?) {
                                                    super.onShowCustomView(view, callback)
                                                    customView = view
                                                    customViewCallback = callback
                                                    isFullscreen = true
                                                }

                                                override fun onHideCustomView() {
                                                    super.onHideCustomView()
                                                    customView = null
                                                    customViewCallback?.onCustomViewHidden()
                                                    customViewCallback = null
                                                    isFullscreen = false
                                                }

                                                override fun onCreateWindow(
                                                    view: WebView?, isDialog: Boolean,
                                                    isUserGesture: Boolean, resultMsg: android.os.Message?
                                                ): Boolean {
                                                    if (view == null) return false
                                                    val tempWebView = WebView(view.context)
                                                    tempWebView.webViewClient = object : WebViewClient() {
                                                        override fun shouldOverrideUrlLoading(v: WebView?, request: WebResourceRequest?): Boolean {
                                                            try { tempWebView.destroy() } catch (_: Exception) {}
                                                            return true
                                                        }
                                                    }
                                                    val transport = resultMsg?.obj as? android.webkit.WebView.WebViewTransport
                                                    transport?.webView = tempWebView
                                                    resultMsg?.sendToTarget()
                                                    return true
                                                }
                                            }
                                            settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36"
                                            val headers = mutableMapOf("Referer" to selectedServerUrl!!)
                                            loadUrl(selectedServerUrl!!, headers)
                                        }
                                    },
                                    update = { webView ->
                                        if (webView.url != selectedServerUrl) {
                                            val headers = mutableMapOf("Referer" to selectedServerUrl!!)
                                            webView.loadUrl(selectedServerUrl!!, headers)
                                        }
                                    },
                                    onRelease = { webView ->
                                        try {
                                            webView.stopLoading()
                                            webView.onPause()
                                            webView.removeAllViews()
                                            webView.destroy()
                                        } catch (_: Exception) {}
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.PlayCircle, null, modifier = Modifier.size(48.dp), tint = themeTextSecondary())
                                    Spacer(Modifier.height(8.dp))
                                    Text("Select a server to start playing", color = themeTextSecondary(), fontFamily = InterFont)
                                }
                            }
                        }
                        
                        // Details below player
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp)
                        ) {
                            // Servers Section
                            val servers = getAvailableServers(
                                type = type,
                                streamingRow = state.streamingRow,
                                movie = movie,
                                episode = selectedEpisode,
                                seasonNumber = if (isSeries) state.seasons.getOrNull(selectedSeasonIdx)?.seasonNumber ?: 1 else 1
                            )

                            if (servers.isNotEmpty()) {
                                Text("Streaming Servers", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = themeTextPrimary(), fontFamily = InterFont)
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    servers.forEachIndexed { index, server ->
                                        val isPremium = index == 0 && servers.size > 1
                                        val isSelected = selectedServerId == server.id || selectedServerUrl == server.url
                                        Box(
                                            modifier = Modifier
                                                .height(40.dp)
                                                .background(
                                                    color = if (isSelected) server.color else server.color.copy(alpha = 0.2f),
                                                    shape = RoundedCornerShape(12.dp)
                                                )
                                                .bounceClick {
                                                    val activateServer = {
                                                        val lowerUrl = server.url.lowercase()
                                                        selectedServerId = server.id
                                                        selectedServerUrl = server.url
                                                        
                                                        if (server.id == "toonplay" && type != "movie" && server.url.startsWith("series-")) {
                                                            android.widget.Toast.makeText(context, "Resolving Stream...", android.widget.Toast.LENGTH_SHORT).show()
                                                            kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                                                val result = com.nexiplay.app.data.util.ToonplayResolver.resolve(
                                                                    toonplayId = server.url,
                                                                    season = if (type == "series" || type == "anime") state.seasons.getOrNull(selectedSeasonIdx)?.seasonNumber ?: 1 else 1,
                                                                    episode = selectedEpisode?.episodeNumber ?: 1
                                                                )
                                                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                                    if (result.url.isNotEmpty() && result.source != "error") {
                                                                        val encoded = java.net.URLEncoder.encode(result.url, "UTF-8")
                                                                        navController.navigate("player/$encoded")
                                                                    } else {
                                                                        android.widget.Toast.makeText(context, "Resolve failed: ${result.error}. Falling back...", android.widget.Toast.LENGTH_SHORT).show()
                                                                        selectedServerUrl = server.url 
                                                                    }
                                                                }
                                                            }
                                                        } else if (lowerUrl.contains(".m3u8") || lowerUrl.contains(".mp4") || lowerUrl.contains(".mkv")) {
                                                            val encoded = java.net.URLEncoder.encode(server.url, "UTF-8")
                                                            navController.navigate("player/$encoded")
                                                        }
                                                    }
                                                    
                                                    val isPremiumLocked = isPremium && !isPremiumUnlocked && !com.nexiplay.app.data.util.AdManager.isUserPremium() && com.nexiplay.app.data.util.AdManager.isPremiumServerAdEnabled
                                                    if (isPremiumLocked) {
                                                        android.widget.Toast.makeText(context, "Loading Ad to unlock premium server...", android.widget.Toast.LENGTH_SHORT).show()
                                                        com.nexiplay.app.data.util.AdManager.showRewardedAd(
                                                            context = context,
                                                            onRewarded = {
                                                                isPremiumUnlocked = true
                                                                activateServer()
                                                            },
                                                            onFailed = {
                                                                android.widget.Toast.makeText(context, "Failed to load ad: $it", android.widget.Toast.LENGTH_SHORT).show()
                                                            }
                                                        )
                                                    } else {
                                                        activateServer()
                                                    }
                                                }
                                                .padding(horizontal = 16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                val isPremiumLocked = isPremium && !isPremiumUnlocked && !com.nexiplay.app.data.util.AdManager.isUserPremium() && com.nexiplay.app.data.util.AdManager.isPremiumServerAdEnabled
                                                if (isPremiumLocked) {
                                                    Icon(Icons.Default.PlayCircle, null, modifier = Modifier.size(14.dp), tint = com.nexiplay.app.ui.theme.GoldVip)
                                                    Spacer(Modifier.width(4.dp))
                                                }
                                                Text(
                                                    text = server.name,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = InterFont,
                                                    color = if (isSelected) Color.White else server.color
                                                )
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(24.dp))
                            }

                            // Episodes Section (for series)
                            if (isSeries && state.seasons.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Seasons & Episodes", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = themeTextPrimary(), fontFamily = InterFont)
                                    TextButton(onClick = { playNextEpisode() }) {
                                        Text("Next Episode ⏭️", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NexiRed, fontFamily = InterFont)
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                
                                ScrollableTabRow(
                                    selectedTabIndex = selectedSeasonIdx,
                                    containerColor = themeCard(),
                                    contentColor = NexiRed,
                                    edgePadding = 0.dp,
                                    divider = {},
                                    indicator = {},
                                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                                ) {
                                    state.seasons.forEachIndexed { idx, season ->
                                        val sel = selectedSeasonIdx == idx
                                        Tab(selected = sel, onClick = { selectedSeasonIdx = idx }) {
                                            Box(
                                                Modifier
                                                    .background(
                                                        if (sel) NexiRed else Color.Transparent,
                                                        RoundedCornerShape(10.dp)
                                                    )
                                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                                            ) {
                                                Text(
                                                    season.seasonTitle ?: "Season ${season.seasonNumber}",
                                                    fontSize = 13.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (sel) themeTextPrimary() else themeTextSecondary(),
                                                    fontFamily = InterFont
                                                )
                                            }
                                        }
                                    }
                                }
                                
                                Spacer(Modifier.height(16.dp))

                                val selectedSeason = state.seasons.getOrNull(selectedSeasonIdx)
                                val episodes = selectedSeason?.let { state.episodes[it.id] } ?: emptyList()

                                if (episodes.isEmpty()) {
                                    Text("No episodes found", color = themeTextTertiary(), fontSize = 13.sp, fontFamily = InterFont)
                                } else {
                                    episodes.forEach { ep ->
                                        val isSelected = selectedEpisode?.id == ep.id
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 8.dp)
                                                .background(if (isSelected) NexiRed.copy(alpha = 0.15f) else themeCard(), RoundedCornerShape(10.dp))
                                                .bounceClick { 
                                                    selectedEpisode = ep 
                                                    val isSeries = type == "series" || type == "anime"
                                                    val currentServers = getAvailableServers(
                                                        type = type,
                                                        streamingRow = state.streamingRow,
                                                        movie = state.movie!!,
                                                        episode = ep,
                                                        seasonNumber = if (isSeries) state.seasons.getOrNull(selectedSeasonIdx)?.seasonNumber ?: 1 else 1
                                                    )
                                                    val matchedServer = currentServers.find { it.id == selectedServerId } ?: currentServers.firstOrNull()
                                                    if (matchedServer != null) {
                                                        selectedServerId = matchedServer.id
                                                        // ToonPlay needs re-resolution on episode switch
                                                        if (matchedServer.id == "toonplay" && type != "movie" && matchedServer.url.startsWith("series-")) {
                                                            selectedServerUrl = null // Show loading state
                                                            kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                                                val result = com.nexiplay.app.data.util.ToonplayResolver.resolve(
                                                                    toonplayId = matchedServer.url,
                                                                    season = if (isSeries) state.seasons.getOrNull(selectedSeasonIdx)?.seasonNumber ?: 1 else 1,
                                                                    episode = ep.episodeNumber
                                                                )
                                                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                                    if (result.url.isNotEmpty() && result.source != "error") {
                                                                        val encoded = java.net.URLEncoder.encode(result.url, "UTF-8")
                                                                        navController.navigate("player/$encoded")
                                                                    } else {
                                                                        selectedServerUrl = matchedServer.url // Fallback to WebView
                                                                    }
                                                                }
                                                            }
                                                        } else {
                                                            selectedServerUrl = matchedServer.url
                                                        }
                                                    } else {
                                                        selectedServerUrl = null
                                                    }
                                                }
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                Modifier
                                                    .size(36.dp)
                                                    .background(if (isSelected) NexiRed else themeBg(), RoundedCornerShape(8.dp)),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Text(
                                                    "${ep.episodeNumber}", 
                                                    fontSize = 14.sp, 
                                                    fontWeight = FontWeight.Bold, 
                                                    color = if (isSelected) Color.White else themeTextSecondary(),
                                                    fontFamily = InterFont
                                                )
                                            }
                                            Spacer(Modifier.width(12.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(
                                                    ep.episodeTitle ?: "Episode ${ep.episodeNumber}",
                                                    fontSize = 14.sp, 
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, 
                                                    color = if (isSelected) NexiRed else themeTextPrimary(),
                                                    fontFamily = InterFont
                                                )
                                            }
                                            if (isSelected) {
                                                Text("PLAYING", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NexiRed, fontFamily = InterFont)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Fullscreen Overlay
        if (isFullscreen && customView != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .zIndex(100f)
            ) {
                AndroidView(
                    factory = { ctx ->
                        FrameLayout(ctx).apply {
                            setBackgroundColor(android.graphics.Color.BLACK)
                        }
                    },
                    update = { frameLayout ->
                        frameLayout.removeAllViews()
                        (customView?.parent as? ViewGroup)?.removeView(customView)
                        customView?.let { 
                            frameLayout.addView(it, FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.Gravity.CENTER
                            ))
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
}
