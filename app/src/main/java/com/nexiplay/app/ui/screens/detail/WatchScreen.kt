package com.nexiplay.app.ui.screens.detail

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Replay10
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
    "taboola", "outbrain", "mgid", "revcontent",
    "spanish-speaking", "darnobedience", "decafeligiblyhad", "bonuscaf", "subgumgutwise"
)

private val AD_PATH_KEYWORDS = listOf(
    "/ads/", "/adserv", "/banner/", "/popup",
    "/pagead/", "/aclk?", "google_ads", "amazon_ads", "prebid", "adsense"
)

private fun isAdUrl(url: String): Boolean {
    val lower = url.lowercase()
    if (lower.contains("disable-devtool") || lower.contains("theajack.github.io") ||
        lower.contains("/__ads/") || lower.contains("soycdn.lol")) {
        return true
    }
    if (lower.contains(".m3u8") || lower.contains(".ts") || lower.contains(".m4s") ||
        lower.contains(".mp4") || lower.contains(".vtt") || lower.contains("/hls/") ||
        lower.contains("widevine") || lower.contains("/license") || lower.contains("/drm/") ||
        lower.contains("playback") || lower.contains("proxy-stream") || lower.contains("get_video")) {
        return false
    }
    if (lower.contains("iamcdn.net") || lower.contains("jwplayer") || lower.contains("hls.js") ||
        lower.contains("video.js") || lower.contains("player") || lower.contains("cloudflare") ||
        lower.contains("unpkg.com") || lower.contains("jsdelivr.net") || lower.contains("google-proxy")) {
        return false
    }
    val host = try { java.net.URI(lower).host ?: "" } catch (e: Exception) { lower }
    for (keyword in AD_HOST_KEYWORDS) { if (host.contains(keyword)) return true }
    for (keyword in AD_PATH_KEYWORDS) { if (lower.contains(keyword)) return true }
    return false
}

private fun normalizeWebPlayerUrl(url: String): String {
    val cleanUrl = url.trim()
    if (cleanUrl.isEmpty()) return ""
    // Prevent double-proxying or re-extracting already normalized player URLs
    if (cleanUrl.contains("/api/player/") || cleanUrl.contains("nexiplay.vercel.app/api/player/")) {
        return cleanUrl
    }
    val lower = cleanUrl.lowercase()
    if ((lower.contains("dood") || lower.contains("ds2play") || lower.contains("playmogo")) && (lower.contains("/d/") || lower.contains("/f/"))) {
        return cleanUrl.replace("/d/", "/e/").replace("/f/", "/e/")
    }
    if (lower.contains("streamtape") || lower.contains("streamta.pe")) {
        if (lower.contains("/v/")) {
            return cleanUrl.replace("/v/", "/e/")
        }
        if (!lower.contains("/e/")) {
            val parts = try { java.net.URI(cleanUrl).path.split("/").filter { it.isNotBlank() } } catch (_: Exception) { emptyList() }
            val id = parts.lastOrNull()
            if (!id.isNullOrBlank()) return "https://streamtape.com/e/$id"
        }
        return cleanUrl
    }
    if (lower.contains("sv2.nontongo.stream/soap/")) {
        return cleanUrl.replace("sv2.nontongo.stream/soap/", "www.nontongo.win/embed/")
    }
    if (lower.contains("vidsrc.pm/embed/")) {
        return cleanUrl.replace("vidsrc.pm/embed/", "vidsrc.buzz/embed/")
    }
    if (lower.contains("2embed.cc/embedtv/")) {
        val tail = cleanUrl.substringAfter("2embed.cc/embedtv/")
        val id = tail.substringBefore("&")
        val s = tail.substringAfter("s=", "1").substringBefore("&")
        val e = tail.substringAfter("e=", "1").substringBefore("&")
        return "https://videm.xyz/embed/tv/$id/$s/$e"
    }
    if (lower.contains("2embed.cc/embed/")) {
        val id = cleanUrl.substringAfter("2embed.cc/embed/").substringBefore("?")
        return "https://videm.xyz/embed/movie/$id"
    }
    if (lower.contains("voe.sx") || lower.contains("voe-network") || lower.contains("ashleyhardat") || lower.contains("shannoncoldalready")) {
        try {
            val uri = java.net.URI(cleanUrl)
            val parts = (uri.path ?: "").split("/").filter { it.isNotBlank() && it != "e" && it != "voe" && it != "api" && it != "player" }
            val id = parts.lastOrNull()
            if (!id.isNullOrBlank() && id != "voe") {
                return "https://voe.sx/e/$id"
            }
        } catch (_: Exception) {}
    }
    if (lower.contains("abyss.to") || lower.contains("abyssplayer") || lower.contains("abysscdn") || lower.contains("short.ink")) {
        try {
            val uri = java.net.URI(cleanUrl)
            val parts = (uri.path ?: "").split("/").filter { it.isNotBlank() && it != "abyss" && it != "player" && it != "api" }
            val id = parts.lastOrNull()
            if (!id.isNullOrBlank() && id != "abyss") {
                return "https://player.abyssplayer.com/$id"
            }
        } catch (_: Exception) {}
    }
    return cleanUrl
}

private fun loadTargetUrlInWebView(webView: android.webkit.WebView, targetUrl: String) {
    val cleanUrl = targetUrl.trim()
    if (cleanUrl.isEmpty() || cleanUrl == "about:blank") {
        webView.loadUrl("about:blank")
        return
    }
    val ref = resolveWebViewReferer(cleanUrl)
    if (ref.isNotBlank()) {
        webView.loadUrl(cleanUrl, mapOf("Referer" to ref))
    } else {
        webView.loadUrl(cleanUrl)
    }
}

private fun buildPlayerEmbedHtml(targetUrl: String): String {
    val cleanUrl = targetUrl.trim()
    val isYouTube = cleanUrl.contains("youtube.com", ignoreCase = true) || cleanUrl.contains("youtu.be", ignoreCase = true)
    val referrerPolicy = "origin-when-cross-origin"

    val escapedUrl = cleanUrl
        .replace("&", "&amp;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
          <meta name="referrer" content="$referrerPolicy">
          <style>
            * { box-sizing: border-box; margin: 0; padding: 0; }
            html, body {
              width: 100%;
              height: 100%;
              background: #000;
              overflow: hidden;
              position: relative;
            }
            iframe {
              position: absolute;
              top: 0;
              left: 0;
              width: 100%;
              height: 100%;
              border: 0;
              outline: 0;
              background: #000;
            }
          </style>
        </head>
        <body>
          <iframe
            id="nexi-player-frame"
            src="$escapedUrl"
            allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share; fullscreen; orientation-lock; screen-wake-lock"
            allowfullscreen="true"
            webkitallowfullscreen="true"
            mozallowfullscreen="true"
            scrolling="no"
            referrerpolicy="$referrerPolicy">
          </iframe>
          <script>
            (function() {
              function attachEndedListener() {
                try {
                  var videos = document.querySelectorAll('video');
                  videos.forEach(function(v) {
                    if (!v.dataset.hasEndHandler) {
                      v.dataset.hasEndHandler = 'true';
                      v.addEventListener('ended', function() {
                        if (window.AndroidBridge && window.AndroidBridge.onVideoEnded) {
                          window.AndroidBridge.onVideoEnded();
                        }
                      });
                    }
                  });
                } catch (_) {}
              }
              attachEndedListener();
              setInterval(attachEndedListener, 2000);

              window.addEventListener('message', function(e) {
                try {
                  var d = e.data;
                  if (typeof d === 'string') {
                    if (d.includes('video_ended') || d.includes('ended') || d.includes('player_complete')) {
                      if (window.AndroidBridge && window.AndroidBridge.onVideoEnded) {
                        window.AndroidBridge.onVideoEnded();
                      }
                    }
                  } else if (d && typeof d === 'object') {
                    if (d.event === 'ended' || d.type === 'ended' || d.status === 'ended') {
                      if (window.AndroidBridge && window.AndroidBridge.onVideoEnded) {
                        window.AndroidBridge.onVideoEnded();
                      }
                    }
                  }
                } catch (_) {}
              });
            })();
          </script>
        </body>
        </html>
    """.trimIndent()
}

private fun createEmptyResponse(): WebResourceResponse = WebResourceResponse(
    "text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0))
)

private fun resolveWebViewReferer(targetUrl: String): String {
    val lower = targetUrl.lowercase()
    return when {
        lower.contains("razorshell.space") || lower.contains("seekplayer") -> "https://codedew.com/"
        lower.contains("codedew.com") || lower.contains("rareanimes") || lower.contains("raretoons") -> "https://rareanimes.net/"
        lower.contains("zephyrflick") || lower.contains("toonstream") -> "https://toonstream.vip/"
        lower.contains("watchanimeworld") || lower.contains("animeworld") -> "https://watchanimeworld.in/"
        lower.contains("animerulz") -> "https://animerulz.net/"
        lower.contains("playmogo") || lower.contains("dood") -> "https://doodstream.com/"
        lower.contains("streamtape") || lower.contains("streamta.pe") -> "https://streamtape.com/"
        lower.contains("voe.sx") || lower.contains("voe") -> "https://voe.sx/"
        lower.contains("abyss") -> "https://abyss.to/"
        lower.contains("vidmoly") -> "https://vidmoly.to/"
        (lower.contains("/embed") && !lower.contains("vidsrc.buzz") && !lower.contains("videm.xyz")) ||
            lower.contains("cinemaos") ||
            lower.contains("vidstuck") ||
            lower.contains("youtube") ||
            lower.contains("youtu.be") ||
            lower.contains("nexiplay.vercel.app") -> "https://nexiplay.vercel.app/"
        else -> ""
    }
}

private val ALLOWED_PLAYER_HOSTS = listOf(
    "cinebros", "cinevu", "framextv", "flaxjoy", "cinesrc", "cineruv",
    "vexo", "atlantic", "vidbolt", "dulo.mov", "rivestream", "vidlove",
    "cinemaos", "movienerds", "screenscape", "vidrock", "videasy", "vidlink",
    "vidsrc", "videm", "vidfast", "2embed", "twoembed", "streamsrcs", "autoembed", "dmca.stream", "wishembed",
    "moviesapi", "vixsrc", "nontongo", "multiembed", "streamingnow", "gdriveplayer",
    "vaplayer", "vidcore", "anyembed", "razorshell", "seekplayer", "codedew",
    "rareanimes", "raretoons", "zephyrflick", "toonstream", "watchanimeworld",
    "animeworld", "animerulz", "animixstream", "youtube", "youtu.be", "googlevideo",
    "rabbitstream", "megacloud", "vidplay", "filemoon", "streamwish", "vidhide",
    "rubystm", "dood", "mixdrop", "voe", "uqload", "streamtape", "mp4upload",
    "abyss", "short.ink", "abyssplayer", "ashleyhardat", "shannoncoldalready", "rendingdecreasehere", "playmogo", "ds2play",
    "circleftp", "moviebox", "aoneroom", "animekhor", "miruro", "aniclover",
    "upcloud", "flixer", "vidnest", "primesrc", "vidspark", "vidstuck", "vidmoly",
    "medmedia", "vidup", "ythd", "soap2day", "soapgate", "streamta.pe",
    "iamcdn.net", "vibeplayer.site", "takutakutaku.workers.dev", "groovy.monster", "nexiplay.vercel.app"
)

private fun isAllowedMainFrameNavigation(targetUrl: String, currentServerUrl: String?): Boolean {
    val lower = targetUrl.lowercase()
    if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false
    if (isAdUrl(lower)) return false
    if (lower.endsWith(".apk") || lower.contains("/download.apk")) return false

    val targetHost = try { java.net.URI(lower).host?.lowercase().orEmpty() } catch (_: Exception) { "" }
    if (targetHost.isEmpty()) return false

    val currentHost = try {
        currentServerUrl?.let { java.net.URI(it.lowercase()).host?.lowercase() }.orEmpty()
    } catch (_: Exception) { "" }

    if (currentHost.isNotEmpty() && (targetHost == currentHost || targetHost.endsWith(".$currentHost") || currentHost.endsWith(".$targetHost"))) {
        return true
    }

    // Allow legitimate sub-navigation/redirects originating from clean proxy/embed players
    if (currentHost.contains("voe") || currentHost.contains("abyss") || currentHost.contains("streamtape") ||
        currentHost.contains("ashleyhardat") || currentHost.contains("shannoncoldalready")) {
        return true
    }

    return ALLOWED_PLAYER_HOSTS.any { allowed -> targetHost.contains(allowed) }
}

internal fun resolveStreamHttpHeaders(streamUrl: String): Map<String, String> {
    val lower = streamUrl.lowercase()
    return when {
        lower.contains("/playback") || lower.contains("vercel.app") -> mapOf(
            "Referer" to "https://nexiplay.vercel.app/",
            "Origin" to "https://nexiplay.vercel.app"
        )
        lower.contains("streamtape.com") || lower.contains("get_video") -> mapOf(
            "Referer" to "https://streamtape.com/",
            "Origin" to "https://streamtape.com"
        )
        lower.contains("anidb.app") || lower.contains("animerulz") -> mapOf(
            "Referer" to "https://animerulz.net/",
            "Origin" to "https://animerulz.net"
        )
        lower.contains("streamindia") || lower.contains("animesalt") || lower.contains("as-cdn21.top") || lower.contains("toonplay.in") -> mapOf(
            "Referer" to "https://toonplay.in/",
            "Origin" to "https://toonplay.in"
        )
        lower.contains("hakunaymatata.com") || lower.contains("netfilm") || lower.contains("aoneroom") -> mapOf(
            "Referer" to "https://netfilm.world/",
            "Origin" to "https://netfilm.world"
        )
        lower.contains("vidvault.to") || lower.contains("workers.dev") -> mapOf(
            "Referer" to "https://vidvault.to/",
            "Origin" to "https://vidvault.to"
        )
        lower.contains("groovy.monster") -> mapOf(
            "Referer" to "https://argon.razorshell.space/",
            "Origin" to "https://argon.razorshell.space"
        )
        lower.contains("razorshell.space") || lower.contains("seekplayer") -> mapOf(
            "Referer" to "https://codedew.com/",
            "Origin" to "https://codedew.com"
        )
        lower.contains("zephyrflick") || lower.contains("toonstream") -> mapOf(
            "Referer" to "https://toonstream.vip/",
            "Origin" to "https://toonstream.vip"
        )
        else -> mapOf(
            "Referer" to "https://nexiplay.vercel.app/",
            "Origin" to "https://nexiplay.vercel.app"
        )
    }
}

internal fun buildStreamMediaItem(targetUrl: String): MediaItem {
    val lower = targetUrl.lowercase()
    val isExplicitFile = lower.contains(".mp4") || lower.contains(".mkv") || lower.contains(".webm") || lower.contains("get_video")
    val isHls = !isExplicitFile && (
        lower.contains(".m3u8") ||
            lower.contains("/hls") ||
            lower.contains("/playback") ||
            lower.contains("as-cdn21.top") ||
            lower.contains("streamindia") ||
            lower.contains("proxy-stream") ||
            lower.contains("hakunaymatata.com")
        )
    val builder = MediaItem.Builder().setUri(Uri.parse(targetUrl))
    if (isHls) {
        builder.setMimeType(MimeTypes.APPLICATION_M3U8)
    } else if (isExplicitFile) {
        builder.setMimeType(MimeTypes.VIDEO_MP4)
    }
    return builder.build()
}
private const val AD_HIDE_JS = """
(function() {
    try {
        Object.defineProperty(window, 'outerWidth', { get: function() { return window.innerWidth; }, configurable: true });
        Object.defineProperty(window, 'outerHeight', { get: function() { return window.innerHeight; }, configurable: true });
        var dummyWin = { focus: function(){}, close: function(){}, closed: false, location: { href: '' } };
        window.open = function() { return dummyWin; };
    } catch(e) {}
    if (!document.getElementById('nexi-adhide-style') && document.head) {
        var style = document.createElement('style');
        style.id = 'nexi-adhide-style';
        style.textContent = `
            ins.adsbygoogle, .adsbygoogle,
            [id*="google_ads"],
            iframe[src*="doubleclick.net"],
            iframe[src*="googlesyndication.com"],
            iframe[src*="exoclick.com"],
            iframe[src*="monetag"],
            iframe[src*="adsterra"],
            iframe[src*="popads"],
            iframe[src*="clickadu"],
            iframe[src*="hilltopads"],
            .ad-overlay, .ad-popup, .ad-interstitial,
            div[class*="push-notification"], div[id*="push-notification"]
            { display: none !important; height: 0 !important; width: 0 !important; overflow: hidden !important; pointer-events: none !important; }
            html, body {
                margin: 0 !important;
                padding: 0 !important;
                background-color: #000 !important;
                width: 100vw !important;
                height: 100vh !important;
                min-width: 100vw !important;
                min-height: 100vh !important;
                overflow: hidden !important;
            }
            #__next, #root, #app {
                width: 100vw !important;
                height: 100vh !important;
                min-width: 100vw !important;
                min-height: 100vh !important;
            }
        `;
        document.head.appendChild(style);
    }
    document.querySelectorAll('iframe').forEach(function(el) {
        if (el.id === 'nexi-player-frame') return;
        var src = (el.src || '').toLowerCase();
        if (src.includes('doubleclick.net') || src.includes('googlesyndication.com') ||
            src.includes('exoclick.com') || src.includes('monetag') || src.includes('propeller') ||
            src.includes('adsterra') || src.includes('popads') || src.includes('clickadu') ||
            src.includes('hilltopads') || src.includes('juicyads')) {
            el.remove();
        }
    });
    // Isolate embedded player iframe on custom anime wrapper pages
    var host = (window.location.hostname || '').toLowerCase();
    var validIframes = Array.from(document.querySelectorAll('iframe')).filter(function(f) {
        if (f.id === 'nexi-player-frame') return false;
        var s = (f.src || f.getAttribute('data-src') || '').toLowerCase();
        return s && !s.includes('about:blank') && !s.includes('doubleclick') && !s.includes('googletagmanager');
    });
    if (validIframes.length > 0 && (host.includes('codedew') || host.includes('rareanimes') || host.includes('raretoons') || host.includes('toonstream') || host.includes('watchanimeworld') || host.includes('zipper'))) {
        var playerIframe = validIframes[0];
        playerIframe.style.cssText = 'position:fixed !important; top:0 !important; left:0 !important; width:100vw !important; height:100vh !important; z-index:2147483647 !important; border:none !important; background:#000 !important;';
    }
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
    var fallbackWebEmbedUrl by remember { mutableStateOf<String?>(null) }
    var selectedServerId by remember { mutableStateOf<String?>(null) }
    var isResolvingServer by remember { mutableStateOf(false) }
    var resolveProgress by remember { mutableFloatStateOf(0.15f) }
    var resolveStatusText by remember { mutableStateOf("Connecting to secure stream...") }
    var selectedSeasonIdx by remember { mutableIntStateOf(0) }
    var selectedEpisodePageIdx by remember { mutableIntStateOf(0) }
    var selectedEpisode by remember { mutableStateOf<Episode?>(null) }
    var isPremiumUnlocked by remember { mutableStateOf(false) }
    var serverFilter by remember { mutableStateOf("all") } // all | hindi | hd
    var isServerSelectorExpanded by remember { mutableStateOf(false) }
    var serverSearchQuery by remember { mutableStateOf("") }
    var episodeSearchQuery by remember { mutableStateOf("") }
    var autoNextEnabled by remember { mutableStateOf(true) }

    val watchedPrefs = remember { context.getSharedPreferences("nexi_watched_eps", android.content.Context.MODE_PRIVATE) }
    var watchedEpisodeIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(state.movie?.id) {
        val mId = state.movie?.id ?: return@LaunchedEffect
        watchedEpisodeIds = watchedPrefs.getStringSet("watched_$mId", emptySet())?.toSet() ?: emptySet()
    }

    val markEpisodeWatched: (String) -> Unit = { epId ->
        state.movie?.id?.let { mId ->
            val updated = (watchedEpisodeIds + epId).toSet()
            watchedEpisodeIds = updated
            watchedPrefs.edit().putStringSet("watched_$mId", HashSet(updated)).apply()
        }
    }

    val scope = rememberCoroutineScope()
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    var resolveJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    // Fullscreen state
    var isFullscreen by remember { mutableStateOf(false) }
    var customView by remember { mutableStateOf<android.view.View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    val activateAndResolveServer: (StreamServer, Int, Int) -> Unit = { server, sNum, eNum ->
        resolveJob?.cancel()
        if (isFullscreen) {
            try { customViewCallback?.onCustomViewHidden() } catch (_: Exception) {}
            customView = null
            customViewCallback = null
            isFullscreen = false
        }
        selectedServerId = server.id
        fallbackWebEmbedUrl = null
        val rawUrl = normalizeWebPlayerUrl(server.url.trim())
        val lowerUrl = rawUrl.lowercase()
        val effectiveType = state.resolvedType.ifBlank { type }

        val seasonEps = state.seasons.getOrNull(selectedSeasonIdx)?.let { state.episodes[it.id] }.orEmpty()
        val idxInSeason = seasonEps.indexOfFirst { it.episodeNumber == eNum }
        val relativeEpNum = if (idxInSeason >= 0) idxInSeason + 1 else eNum

        val needsWrapperResolution = server.id == "rareanimes" ||
            server.id == "animekhor" ||
            server.id == "moviebox" ||
            lowerUrl.contains("rareanimes.") ||
            lowerUrl.contains("raretoons.") ||
            lowerUrl.contains("codedew.com/zipper") ||
            lowerUrl.contains("/zipper/") ||
            lowerUrl.contains("animekhor") ||
            lowerUrl.contains("moviebox") ||
            lowerUrl.contains("aoneroom") ||
            rawUrl.matches(Regex("""^\d+$""")) ||
            ((lowerUrl.contains("toonstream") ||
                lowerUrl.contains("watchanimeworld") ||
                lowerUrl.contains("animeworld") ||
                lowerUrl.contains("animixstream") ||
                lowerUrl.contains("animerulz") ||
                lowerUrl.contains("netfilm") ||
                lowerUrl.contains("moviebox") ||
                lowerUrl.contains("sflix") ||
                lowerUrl.contains("miruro") ||
                lowerUrl.contains("aniclover") ||
                lowerUrl.contains("aoneroom")) &&
                !lowerUrl.contains("/embed/") && !lowerUrl.contains("/e/") && !lowerUrl.contains("player"))

        if (rawUrl.startsWith("toonplay://") || (server.id == "toonplay" && effectiveType != "movie" && (rawUrl.startsWith("series-") || rawUrl.startsWith("anime-")))) {
            // Keep WebView mounted (never set selectedServerUrl = null while WebView exists) to avoid native Chromium SIGSEGV
            try {
                webViewRef.value?.stopLoading()
                webViewRef.value?.tag = "about:blank"
                webViewRef.value?.loadUrl("about:blank")
            } catch (_: Exception) {}
            isResolvingServer = true
            val withoutScheme = rawUrl.removePrefix("toonplay://")
            val tpId = withoutScheme.substringBefore("/").substringBefore("?")
            val cachedTpUrl = if (withoutScheme.contains("?cached=")) {
                try {
                    java.net.URLDecoder.decode(withoutScheme.substringAfter("?cached="), "UTF-8")
                } catch (_: Exception) { null }
            } else null
            resolveJob = scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val result = com.nexiplay.app.data.util.ToonplayResolver.resolve(
                    toonplayId = tpId,
                    season = sNum,
                    episode = eNum,
                    cachedUrl = cachedTpUrl
                )
                val finalUrl = if (result.url.isNotEmpty() && result.source != "error") {
                    result.url
                } else {
                    com.nexiplay.app.data.repository.WebFeaturesRepository.resolveToonplayStream(tpId, sNum, eNum, cachedTpUrl) ?: cachedTpUrl.orEmpty()
                }
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    isResolvingServer = false
                    if (finalUrl.isNotEmpty()) {
                        selectedServerUrl = finalUrl
                    } else {
                        android.widget.Toast.makeText(context, "Couldn't open this stream. Try another server.", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } else if (
            lowerUrl.contains(".m3u8") ||
            lowerUrl.contains("/hls") ||
            lowerUrl.contains("/playback") ||
            lowerUrl.contains(".mp4") ||
            lowerUrl.contains(".mkv") ||
            lowerUrl.contains("google-proxy") ||
            lowerUrl.contains("proxy-stream") ||
            lowerUrl.contains("as-cdn21.top") ||
            lowerUrl.contains("streamindia") ||
            lowerUrl.contains("get_video") ||
            lowerUrl.contains("fallback.streamindia.co.in/sources") ||
            lowerUrl.contains("hakunaymatata.com")
        ) {
            isResolvingServer = false
            try {
                webViewRef.value?.stopLoading()
                webViewRef.value?.tag = "about:blank"
                webViewRef.value?.loadUrl("about:blank")
            } catch (_: Exception) {}
            selectedServerUrl = rawUrl
        } else if (server.id == "rareanimes" || lowerUrl.contains("/embed/rr")) {
            try {
                webViewRef.value?.stopLoading()
                webViewRef.value?.tag = "about:blank"
                webViewRef.value?.loadUrl("about:blank")
            } catch (_: Exception) {}
            isResolvingServer = true
            resolveProgress = 0.20f
            resolveStatusText = "Connecting to RR Nexiplay server..."
            resolveJob = scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val fallbackCandidates = buildList {
                    state.movie?.scraperUrl?.trim()?.takeIf { it.isNotBlank() && !it.startsWith("{") }?.let { add(it) }
                    val rawMulti = state.streamingRow?.multiScraperConfig?.takeIf { it.isNotBlank() }
                        ?: if (state.movie?.scraperSource == "multi") state.movie?.scraperUrl else null
                    if (!rawMulti.isNullOrBlank()) {
                        Regex("""https?://[^\s"'\\}]+""").findAll(rawMulti).forEach { m ->
                            val u = m.value.trim()
                            if (u.contains("rareanimes", ignoreCase = true) ||
                                u.contains("codedew", ignoreCase = true) ||
                                u.contains("razorshell", ignoreCase = true) ||
                                u.contains("/zipper/", ignoreCase = true)
                            ) {
                                add(u)
                            }
                        }
                    }
                    state.episodes.values.flatten().forEach { epItem ->
                        val epRaw = epItem.streamingUrl?.trim().orEmpty()
                        if (epRaw.contains("rareanimes", ignoreCase = true) ||
                            epRaw.contains("codedew", ignoreCase = true) ||
                            epRaw.contains("razorshell", ignoreCase = true)
                        ) {
                            Regex("""https?://[^\s"'\\}]+""").findAll(epRaw).forEach { m ->
                                val u = m.value.trim()
                                if (u.contains("rareanimes", ignoreCase = true) ||
                                    u.contains("codedew", ignoreCase = true) ||
                                    u.contains("razorshell", ignoreCase = true) ||
                                    u.contains("/zipper/", ignoreCase = true)
                                ) {
                                    add(u)
                                }
                            }
                        }
                    }
                }.distinct()

                val streamData = com.nexiplay.app.data.repository.WebFeaturesRepository.resolveRRStream(
                    rawUrl = rawUrl,
                    id = state.movie?.id.orEmpty(),
                    slug = state.movie?.slug.orEmpty(),
                    season = sNum,
                    episode = eNum,
                    relativeEpisode = relativeEpNum,
                    fallbackUrls = fallbackCandidates,
                    onProgress = { p, msg ->
                        scope.launch(kotlinx.coroutines.Dispatchers.Main) {
                            resolveProgress = p
                            resolveStatusText = msg
                        }
                    }
                )
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    isResolvingServer = false
                    val playback = streamData?.playbackUrl?.takeIf { it.isNotBlank() }
                    val embed = streamData?.embedUrl?.takeIf { it.isNotBlank() }
                    fallbackWebEmbedUrl = embed?.takeIf { it != playback }
                    selectedServerUrl = playback ?: embed ?: rawUrl
                    if (playback == null && embed == null) {
                        android.widget.Toast.makeText(context, "Mirror stream loading... Please tap another server if playback doesn't start.", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } else if (server.id == "streamtape" || lowerUrl.contains("streamtape") || lowerUrl.contains("streamta.pe")) {
            try {
                webViewRef.value?.stopLoading()
                webViewRef.value?.tag = "about:blank"
                webViewRef.value?.loadUrl("about:blank")
            } catch (_: Exception) {}
            isResolvingServer = true
            resolveJob = scope.launch {
                val stResult = com.nexiplay.app.data.repository.WebFeaturesRepository.resolveStreamtapeLocally(rawUrl)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    isResolvingServer = false
                    if (stResult.directUrl != null) {
                        fallbackWebEmbedUrl = stResult.embedUrl
                        selectedServerUrl = stResult.directUrl
                    } else {
                        fallbackWebEmbedUrl = null
                        selectedServerUrl = stResult.embedUrl
                    }
                }
            }
        } else if (needsWrapperResolution) {
            // Keep WebView mounted and blanked while resolving next episode's embed URL
            try {
                webViewRef.value?.stopLoading()
                webViewRef.value?.tag = "about:blank"
                webViewRef.value?.loadUrl("about:blank")
            } catch (_: Exception) {}
            isResolvingServer = true
            resolveJob = scope.launch {
                val resolved = com.nexiplay.app.data.repository.WebFeaturesRepository.resolveWebEmbedUrl(
                    embedUrl = rawUrl,
                    episodeNum = eNum,
                    relativeEpisodeNum = relativeEpNum,
                    seasonNum = sNum
                )
                isResolvingServer = false
                val finalTarget = resolved.takeIf { it.isNotBlank() } ?: rawUrl
                selectedServerUrl = normalizeWebPlayerUrl(finalTarget)
            }
        } else {
            isResolvingServer = false
            selectedServerUrl = rawUrl
        }
    }

    LaunchedEffect(type, slug) {
        vm.loadContent(type, slug)
    }

    // Auto-select target or first episode for series
    LaunchedEffect(state.seasons, state.episodes, state.resolvedType) {
        val actualType = state.resolvedType
        if ((actualType == "series" || actualType == "anime") && state.seasons.isNotEmpty() && selectedEpisode == null) {
            val (targetSeasonNum, targetEpNum) = DetailViewModel.consumePendingWatchTarget()
            val sIdx = if (targetSeasonNum != null) {
                state.seasons.indexOfFirst { it.seasonNumber == targetSeasonNum }.takeIf { it >= 0 } ?: 0
            } else 0
            selectedSeasonIdx = sIdx
            val targetSeason = state.seasons.getOrNull(sIdx) ?: state.seasons.first()
            val episodes = state.episodes[targetSeason.id]
            if (!episodes.isNullOrEmpty()) {
                val chosenEp = if (targetEpNum != null) {
                    episodes.firstOrNull { it.episodeNumber == targetEpNum } ?: episodes.first()
                } else {
                    episodes.first()
                }
                selectedEpisode = chosenEp
                markEpisodeWatched(chosenEp.id)
            }
        }
    }

    // Auto-select top server when content/episodes load
    LaunchedEffect(state.movie, selectedEpisode, state.streamingRow, state.resolvedType) {
        val actualType = state.resolvedType
        val isSeries = actualType == "series" || actualType == "anime"
        if (isSeries && selectedEpisode == null && state.seasons.isNotEmpty()) return@LaunchedEffect

        if (state.movie != null && selectedServerUrl == null && selectedServerId == null) {
            val sNum = if (isSeries) state.seasons.getOrNull(selectedSeasonIdx)?.seasonNumber ?: 1 else 1
            val currentServers = getAvailableServers(
                type = actualType,
                streamingRow = state.streamingRow,
                movie = state.movie!!,
                episode = selectedEpisode,
                seasonNumber = sNum
            )
            val firstServer = currentServers.firstOrNull()
            if (firstServer != null) {
                activateAndResolveServer(firstServer, sNum, selectedEpisode?.episodeNumber ?: 1)
            }
        }
    }

    // Helper: Play Previous Episode
    val playPrevEpisode: () -> Unit = {
        val effectiveType = state.resolvedType.ifBlank { type }
        val isSeries = effectiveType == "series" || effectiveType == "anime"
        if (isSeries && state.seasons.isNotEmpty() && state.movie != null) {
            val currentSeason = state.seasons.getOrNull(selectedSeasonIdx)
            val episodes = currentSeason?.let { state.episodes[it.id] } ?: emptyList()
            val currentEpIdx = episodes.indexOfFirst { it.id == selectedEpisode?.id }

            val prevEp: Episode?
            val prevSeasonIdx: Int

            if (currentEpIdx > 0) {
                prevEp = episodes[currentEpIdx - 1]
                prevSeasonIdx = selectedSeasonIdx
            } else if (selectedSeasonIdx > 0) {
                prevSeasonIdx = selectedSeasonIdx - 1
                val prevSeason = state.seasons[prevSeasonIdx]
                prevEp = state.episodes[prevSeason.id]?.lastOrNull()
            } else {
                prevEp = null
                prevSeasonIdx = selectedSeasonIdx
            }

            if (prevEp != null) {
                selectedSeasonIdx = prevSeasonIdx
                selectedEpisode = prevEp
                markEpisodeWatched(prevEp.id)
                val sNum = state.seasons.getOrNull(prevSeasonIdx)?.seasonNumber ?: 1
                val currentServers = getAvailableServers(
                    type = effectiveType,
                    streamingRow = state.streamingRow,
                    movie = state.movie!!,
                    episode = prevEp,
                    seasonNumber = sNum
                )
                val matchedServer = currentServers.find { it.id == selectedServerId } ?: currentServers.firstOrNull()
                if (matchedServer != null) {
                    activateAndResolveServer(matchedServer, sNum, prevEp.episodeNumber)
                }
            }
        }
    }

    // Helper: Play Next Episode
    val playNextEpisode: () -> Unit = {
        val effectiveType = state.resolvedType.ifBlank { type }
        val isSeries = effectiveType == "series" || effectiveType == "anime"
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
                markEpisodeWatched(nextEp.id)
                val sNum = state.seasons.getOrNull(nextSeasonIdx)?.seasonNumber ?: 1
                val currentServers = getAvailableServers(
                    type = effectiveType,
                    streamingRow = state.streamingRow,
                    movie = state.movie!!,
                    episode = nextEp,
                    seasonNumber = sNum
                )
                val matchedServer = currentServers.find { it.id == selectedServerId } ?: currentServers.firstOrNull()
                if (matchedServer != null) {
                    activateAndResolveServer(matchedServer, sNum, nextEp.episodeNumber)
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
            if (autoNextEnabled) {
                playNextEpisode()
            }
        }
    }

    // Track Watch History
    var eventId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.movie, selectedEpisode) {
        state.movie?.let { movie ->
            val isSeries = (movie.type == "series" || movie.type == "anime")
            if (isSeries && selectedEpisode == null && state.seasons.isNotEmpty()) return@LaunchedEffect

            val user = SupabaseClient.main.auth.currentUserOrNull()
            if (user != null) {
                try {
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

    // Fullscreen handling: smooth landscape immersive transition with WindowCompat
    DisposableEffect(isFullscreen) {
        val window = activity?.window
        val insetsController = if (window != null) {
            androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        } else null

        if (isFullscreen) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            insetsController?.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            insetsController?.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            insetsController?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            insetsController?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
    }


    Box(modifier = Modifier.fillMaxSize()) {
        androidx.activity.compose.BackHandler(enabled = isFullscreen) {
            try { customViewCallback?.onCustomViewHidden() } catch (_: Exception) {}
            customView = null
            customViewCallback = null
            isFullscreen = false
        }

    Scaffold(
        topBar = {
            if (!isFullscreen) {
                TopAppBar(
                title = {
                    if (state.isLoading) {
                        ShimmerBox(modifier = Modifier.width(140.dp).height(20.dp), cornerRadius = 6)
                    } else if (state.movie != null) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                state.movie!!.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = themeTextPrimary(),
                                fontFamily = InterFont
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (selectedEpisode != null) {
                                    val sNum = state.seasons.getOrNull(selectedSeasonIdx)?.seasonNumber ?: 1
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = NexiRed.copy(alpha = 0.16f)
                                    ) {
                                        Text(
                                            "S$sNum • E${selectedEpisode!!.episodeNumber}",
                                            fontSize = 10.sp,
                                            color = NexiRed,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontFamily = InterFont,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                        )
                                    }
                                    Text(
                                        selectedEpisode!!.episodeTitle ?: "Episode ${selectedEpisode!!.episodeNumber}",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontSize = 11.sp,
                                        color = themeTextSecondary(),
                                        fontWeight = FontWeight.Medium,
                                        fontFamily = InterFont
                                    )
                                } else {
                                    Text(
                                        state.movie!!.type.replaceFirstChar { it.uppercase() },
                                        fontSize = 11.sp,
                                        color = themeTextSecondary(),
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = InterFont
                                    )
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(themeCard(), RoundedCornerShape(10.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            WatchSvgIcon(type = "back", tint = themeTextPrimary(), size = 16.dp)
                        }
                    }
                },
                actions = {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color(0xFF10B981).copy(alpha = 0.12f),
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .border(1.dp, Color(0xFF10B981).copy(alpha = 0.28f), RoundedCornerShape(50))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            WatchSvgIcon(type = "shield", tint = Color(0xFF10B981), size = 12.dp)
                            Text(
                                "AdShield ON",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF10B981),
                                fontFamily = InterFont
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = themeBg())
            )
            }
        },
        containerColor = themeBg()
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isFullscreen) PaddingValues(0.dp) else padding)
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
                    val effectiveType = state.resolvedType.ifBlank { type }
                    val isSeries = effectiveType == "series" || effectiveType == "anime"
                    val currentSeasonNum = if (isSeries) state.seasons.getOrNull(selectedSeasonIdx)?.seasonNumber ?: 1 else 1
                    val servers = remember(effectiveType, state.streamingRow, movie, selectedEpisode, currentSeasonNum) {
                        getAvailableServers(
                            type = effectiveType,
                            streamingRow = state.streamingRow,
                            movie = movie,
                            episode = selectedEpisode,
                            seasonNumber = currentSeasonNum
                        )
                    }

                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Player Container
                        val isDirectVideoStream = remember(selectedServerUrl) {
                            val u = selectedServerUrl?.lowercase().orEmpty()
                            u.contains(".m3u8") ||
                                u.contains("/hls") ||
                                u.contains("/playback") ||
                                u.contains(".mp4") ||
                                u.contains(".mkv") ||
                                u.contains("google-proxy") ||
                                u.contains("proxy-stream") ||
                                u.contains("as-cdn21.top") ||
                                u.contains("streamindia") ||
                                u.contains("circleftp") ||
                                u.contains("movieboxhd") ||
                                u.contains("aoneroom") ||
                                u.contains("get_video") ||
                                u.contains("fallback.streamindia.co.in/sources") ||
                                u.contains("hakunaymatata.com")
                        }
                        Box(
                            modifier = if (isFullscreen) {
                                Modifier.fillMaxSize().background(Color.Black)
                            } else {
                                Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)
                            },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selectedServerUrl != null && isDirectVideoStream) {
                                InlineWatchPlayer(
                                    streamUrl = selectedServerUrl!!,
                                    onVideoEnded = {
                                        if (autoNextEnabled) {
                                            playNextEpisode()
                                        }
                                    },
                                    onOpenFullscreen = {
                                        val encoded = java.net.URLEncoder.encode(selectedServerUrl!!, "UTF-8")
                                        navController.navigate("player/$encoded")
                                    },
                                    onPlaybackError = fallbackWebEmbedUrl?.let { fbEmbed ->
                                        {
                                            fallbackWebEmbedUrl = null
                                            selectedServerUrl = fbEmbed
                                        }
                                    }
                                )
                            } else if (selectedServerUrl != null) {
                                AndroidView(
                                    factory = { ctx ->
                                        WebView(ctx).apply {
                                            webViewRef.value = this
                                            layoutParams = ViewGroup.LayoutParams(
                                                ViewGroup.LayoutParams.MATCH_PARENT,
                                                ViewGroup.LayoutParams.MATCH_PARENT
                                            )
                                            setBackgroundColor(android.graphics.Color.BLACK)
                                            setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                                            settings.javaScriptEnabled = true
                                            settings.domStorageEnabled = true
                                            settings.databaseEnabled = true
                                            settings.mediaPlaybackRequiresUserGesture = false
                                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                            settings.setSupportMultipleWindows(true)
                                            settings.javaScriptCanOpenWindowsAutomatically = false
                                            settings.useWideViewPort = false
                                            settings.loadWithOverviewMode = false
                                            CookieManager.getInstance().setAcceptCookie(true)
                                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                            
                                            addJavascriptInterface(object {
                                                @android.webkit.JavascriptInterface
                                                fun onVideoEnded() {
                                                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                                                        if (autoNextEnabled) {
                                                            playNextEpisode()
                                                        }
                                                    }
                                                }
                                            }, "AndroidBridge")

                                            webViewClient = object : WebViewClient() {
                                                override fun shouldOverrideUrlLoading(
                                                    view: WebView?,
                                                    request: WebResourceRequest?
                                                ): Boolean {
                                                    val reqUrl = request?.url?.toString() ?: return false
                                                    if (reqUrl == "about:blank" || reqUrl.startsWith("data:")) return false
                                                    if (!reqUrl.startsWith("http://", ignoreCase = true) && !reqUrl.startsWith("https://", ignoreCase = true)) {
                                                        return true
                                                    }
                                                    if (isAdUrl(reqUrl) || reqUrl.contains("disable-devtool", ignoreCase = true) || reqUrl.contains("theajack.github.io", ignoreCase = true)) {
                                                        return true
                                                    }
                                                    if (request.isForMainFrame) {
                                                        val isBase = reqUrl.startsWith("https://nexiplay.vercel.app", ignoreCase = true) ||
                                                            reqUrl.startsWith("http://localhost", ignoreCase = true)
                                                        if (!isBase) {
                                                            val currentTarget = view?.tag as? String ?: selectedServerUrl
                                                            if (!isAllowedMainFrameNavigation(reqUrl, currentTarget)) {
                                                                return true
                                                            }
                                                        }
                                                    }
                                                    return false
                                                }

                                                override fun shouldInterceptRequest(
                                                    view: WebView?, request: WebResourceRequest?
                                                ): WebResourceResponse? {
                                                    val requestUrl = request?.url?.toString() ?: return null
                                                    if (isAdUrl(requestUrl)) {
                                                        return WebResourceResponse(
                                                            "text/javascript",
                                                            "UTF-8",
                                                            ByteArrayInputStream("window.adsbygoogle = window.adsbygoogle || [];".toByteArray())
                                                        )
                                                    }
                                                    return super.shouldInterceptRequest(view, request)
                                                }

                                                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                                    super.onPageStarted(view, url, favicon)
                                                    if (url == null || url == "about:blank") return
                                                    view?.evaluateJavascript(AD_HIDE_JS, null)
                                                }

                                                override fun onPageCommitVisible(view: WebView?, url: String?) {
                                                    super.onPageCommitVisible(view, url)
                                                    if (url == null || url == "about:blank") return
                                                    view?.evaluateJavascript(AD_HIDE_JS, null)
                                                }

                                                override fun onPageFinished(view: WebView?, url: String?) {
                                                    super.onPageFinished(view, url)
                                                    if (url == null || url == "about:blank") return
                                                    view?.evaluateJavascript(AD_HIDE_JS, null)
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
                                                    // Block popup windows cleanly without creating/destroying a temporary WebView
                                                    return false
                                                }

                                                override fun onPermissionRequest(request: PermissionRequest?) {
                                                    try {
                                                        request?.grant(request.resources)
                                                    } catch (_: Exception) {
                                                        super.onPermissionRequest(request)
                                                    }
                                                }
                                            }
                                            settings.userAgentString = "Mozilla/5.0 (Linux; Android 14; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
                                            val target = selectedServerUrl!!
                                            tag = target
                                            loadTargetUrlInWebView(this, target)
                                        }
                                    },
                                    update = { webView ->
                                        webViewRef.value = webView
                                        val target = selectedServerUrl
                                        if (target != null && !isResolvingServer && webView.tag != target) {
                                            webView.tag = target
                                            loadTargetUrlInWebView(webView, target)
                                        }
                                    },
                                    onRelease = { webView ->
                                        webViewRef.value = null
                                        try {
                                            webView.stopLoading()
                                            webView.onPause()
                                            webView.loadUrl("about:blank")
                                        } catch (_: Exception) {}
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            if (isResolvingServer) {
                                val animatedProgress by androidx.compose.animation.core.animateFloatAsState(
                                    targetValue = resolveProgress.coerceIn(0.1f, 1.0f),
                                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 350),
                                    label = "resolve_progress_anim"
                                )
                                val pct = (animatedProgress * 100).toInt().coerceIn(10, 100)

                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color(0xFF090D14)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Card(
                                        shape = RoundedCornerShape(20.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF131926).copy(alpha = 0.95f)),
                                        border = BorderStroke(1.dp, Color(0xFF6366F1).copy(alpha = 0.35f)),
                                        modifier = Modifier
                                            .padding(horizontal = 24.dp)
                                            .fillMaxWidth(0.9f)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .padding(vertical = 24.dp, horizontal = 20.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(64.dp)) {
                                                CircularProgressIndicator(
                                                    progress = { animatedProgress },
                                                    modifier = Modifier.fillMaxSize(),
                                                    color = Color(0xFF818CF8),
                                                    trackColor = Color.White.copy(alpha = 0.08f),
                                                    strokeWidth = 4.dp
                                                )
                                                Text(
                                                    "$pct%",
                                                    color = Color.White,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = InterFont
                                                )
                                            }

                                            Spacer(Modifier.height(14.dp))

                                            Text(
                                                text = resolveStatusText,
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                fontFamily = InterFont,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            )

                                            Spacer(Modifier.height(6.dp))

                                            Text(
                                                text = "Connecting secure player tunnel...",
                                                color = Color.White.copy(alpha = 0.6f),
                                                fontSize = 11.sp,
                                                fontFamily = InterFont,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            )

                                            Spacer(Modifier.height(14.dp))

                                            LinearProgressIndicator(
                                                progress = { animatedProgress },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(5.dp)
                                                    .clip(RoundedCornerShape(3.dp)),
                                                color = Color(0xFF6366F1),
                                                trackColor = Color.White.copy(alpha = 0.08f)
                                            )
                                        }
                                    }
                                }
                            } else if (selectedServerUrl == null) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.PlayCircle, null, modifier = Modifier.size(48.dp), tint = themeTextSecondary())
                                    Spacer(Modifier.height(8.dp))
                                    Text("Select a server to start playing", color = themeTextSecondary(), fontFamily = InterFont)
                                }
                            }

                            if (isFullscreen && customView == null) {
                                IconButton(
                                    onClick = {
                                        try { customViewCallback?.onCustomViewHidden() } catch (_: Exception) {}
                                        customView = null
                                        customViewCallback = null
                                        isFullscreen = false
                                    },
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(16.dp)
                                        .size(38.dp)
                                        .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                                ) {
                                    WatchSvgIcon(type = "back", tint = Color.White, size = 18.dp)
                                }
                            }
                        }

                        // ── Clean Player Action Bar (equal-width, never clipped) ──
                        if (!isFullscreen) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(themeCard())
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isSeries && state.seasons.isNotEmpty()) {
                                    WatchActionButton(
                                        icon = "prev_ep",
                                        label = "Prev",
                                        tint = themeTextPrimary(),
                                        modifier = Modifier.weight(1f),
                                        onClick = { playPrevEpisode() }
                                    )
                                    WatchActionButton(
                                        icon = "next_ep",
                                        label = "Next",
                                        tint = NexiRed,
                                        highlighted = true,
                                        modifier = Modifier.weight(1f),
                                        onClick = { playNextEpisode() }
                                    )
                                    WatchActionButton(
                                        icon = "auto_next",
                                        label = if (autoNextEnabled) "Auto On" else "Auto Off",
                                        tint = if (autoNextEnabled) Color(0xFF10B981) else themeTextSecondary(),
                                        highlighted = autoNextEnabled,
                                        modifier = Modifier.weight(1f),
                                        onClick = { autoNextEnabled = !autoNextEnabled }
                                    )
                                }
                                if (servers.size > 1) {
                                    WatchActionButton(
                                        icon = "switch_server",
                                        label = "Server",
                                        tint = Color(0xFFF59E0B),
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            val currIdx = servers.indexOfFirst { if (selectedServerId != null) it.id == selectedServerId else it.url == selectedServerUrl }
                                            val nextServer = servers[(if (currIdx >= 0) currIdx + 1 else 0) % servers.size]
                                            activateAndResolveServer(nextServer, currentSeasonNum, selectedEpisode?.episodeNumber ?: 1)
                                        }
                                    )
                                }
                                WatchActionButton(
                                    icon = "refresh",
                                    label = "Reload",
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        val target = selectedServerUrl
                                        if (target != null) {
                                            webViewRef.value?.let { wb ->
                                                wb.tag = null
                                                wb.tag = target
                                                loadTargetUrlInWebView(wb, target)
                                            }
                                        }
                                    }
                                )
                                WatchActionButton(
                                    icon = "fullscreen",
                                    label = "Full",
                                    tint = if (isSeries) themeTextPrimary() else NexiRed,
                                    highlighted = !isSeries,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        if (isDirectVideoStream && selectedServerUrl != null) {
                                            val encoded = java.net.URLEncoder.encode(selectedServerUrl!!, "UTF-8")
                                            navController.navigate("player/$encoded")
                                        } else {
                                            isFullscreen = true
                                        }
                                    }
                                )
                            }
                        HorizontalDivider(color = Color.White.copy(alpha = 0.06f), thickness = 1.dp)

                        // Details below player
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // ── Now Playing Info ──
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = movie.title,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = themeTextPrimary(),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    fontFamily = InterFont
                                )
                                if (isSeries && selectedEpisode != null) {
                                    Text(
                                        text = "Season $currentSeasonNum · Episode ${selectedEpisode!!.episodeNumber}" +
                                            (selectedEpisode!!.episodeTitle?.takeIf { it.isNotBlank() && !it.equals("Episode ${selectedEpisode!!.episodeNumber}", true) }?.let { " — $it" } ?: ""),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = themeTextSecondary(),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontFamily = InterFont
                                    )
                                }
                            }

                            // ── Server Selector (compact 2-column grid) ──
                            if (servers.isNotEmpty()) {
                                val hindiServers = remember(servers) { servers.filter { it.isHindi } }
                                val hdServers = remember(servers) { servers.filter { !it.isHindi && it.id != "trailer" } }

                                val filteredServers = remember(servers, serverFilter, serverSearchQuery) {
                                    val byTab = when (serverFilter) {
                                        "hindi" -> hindiServers
                                        "hd" -> hdServers
                                        else -> servers
                                    }
                                    if (serverSearchQuery.isBlank()) byTab
                                    else {
                                        val q = serverSearchQuery.trim().lowercase()
                                        byTab.filter {
                                            it.name.lowercase().contains(q) ||
                                                it.badge.lowercase().contains(q) ||
                                                it.id.lowercase().contains(q)
                                        }
                                    }
                                }

                                val triggerServerSelection: (StreamServer, Int) -> Unit = { server, idx ->
                                    val isPremium = idx == 0 && serverFilter == "all" && servers.size > 1
                                    val activateServer = {
                                        activateAndResolveServer(
                                            server,
                                            currentSeasonNum,
                                            selectedEpisode?.episodeNumber ?: 1
                                        )
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

                                val collapsedCount = 6
                                val visibleServers = if (isServerSelectorExpanded) filteredServers else filteredServers.take(collapsedCount)

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(themeCard(), RoundedCornerShape(16.dp))
                                        .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    WatchSectionHeader(
                                        icon = "layers",
                                        title = "Servers",
                                        count = "${servers.size}"
                                    )

                                    // Segmented filter control (equal width)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(themeBg(), RoundedCornerShape(12.dp))
                                            .padding(4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        listOf(
                                            Triple("all", "All", servers.size),
                                            Triple("hindi", "Hindi", hindiServers.size),
                                            Triple("hd", "HD", hdServers.size)
                                        ).forEach { (key, label, count) ->
                                            val active = serverFilter == key
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(9.dp))
                                                    .background(if (active) NexiRed else Color.Transparent)
                                                    .clickable { serverFilter = key }
                                                    .padding(vertical = 8.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    "$label ($count)",
                                                    fontSize = 12.sp,
                                                    fontWeight = if (active) FontWeight.ExtraBold else FontWeight.SemiBold,
                                                    color = if (active) Color.White else themeTextSecondary(),
                                                    fontFamily = InterFont,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }

                                    if (isServerSelectorExpanded && servers.size > 8) {
                                        OutlinedTextField(
                                            value = serverSearchQuery,
                                            onValueChange = { serverSearchQuery = it },
                                            placeholder = { Text("Search servers…", fontSize = 12.sp, color = themeTextTertiary()) },
                                            leadingIcon = { WatchSvgIcon(type = "search", tint = themeTextSecondary(), size = 15.dp) },
                                            trailingIcon = {
                                                if (serverSearchQuery.isNotEmpty()) {
                                                    IconButton(onClick = { serverSearchQuery = "" }) {
                                                        WatchSvgIcon(type = "close", tint = themeTextSecondary(), size = 13.dp)
                                                    }
                                                }
                                            },
                                            singleLine = true,
                                            shape = RoundedCornerShape(11.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = NexiRed,
                                                unfocusedBorderColor = Color.White.copy(alpha = 0.08f),
                                                focusedContainerColor = themeBg(),
                                                unfocusedContainerColor = themeBg(),
                                                focusedTextColor = themeTextPrimary(),
                                                unfocusedTextColor = themeTextPrimary()
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }

                                    if (filteredServers.isEmpty()) {
                                        Text(
                                            "No servers in this category",
                                            fontSize = 12.sp,
                                            color = themeTextTertiary(),
                                            fontFamily = InterFont
                                        )
                                    } else {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            visibleServers.chunked(2).forEachIndexed { rowIdx, rowServers ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    rowServers.forEachIndexed { colIdx, server ->
                                                        val index = rowIdx * 2 + colIdx
                                                        val isSelected = if (selectedServerId != null) selectedServerId == server.id else selectedServerUrl == server.url
                                                        val isPremium = index == 0 && serverFilter == "all" && servers.size > 1
                                                        val isPremiumLocked = isPremium && !isPremiumUnlocked && !com.nexiplay.app.data.util.AdManager.isUserPremium() && com.nexiplay.app.data.util.AdManager.isPremiumServerAdEnabled
                                                        Row(
                                                            modifier = Modifier
                                                                .weight(1f)
                                                                .height(52.dp)
                                                                .clip(RoundedCornerShape(12.dp))
                                                                .background(if (isSelected) server.color.copy(alpha = 0.16f) else themeBg())
                                                                .border(
                                                                    1.dp,
                                                                    if (isSelected) server.color else Color.White.copy(alpha = 0.06f),
                                                                    RoundedCornerShape(12.dp)
                                                                )
                                                                .clickable { triggerServerSelection(server, index) }
                                                                .padding(horizontal = 10.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(9.dp)
                                                        ) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(30.dp)
                                                                    .background(server.color.copy(alpha = 0.16f), RoundedCornerShape(8.dp)),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                if (isPremiumLocked) {
                                                                    WatchSvgIcon(type = "crown", tint = com.nexiplay.app.ui.theme.GoldVip, size = 14.dp)
                                                                } else if (isSelected) {
                                                                    WatchSvgIcon(type = "check", tint = server.color, size = 14.dp)
                                                                } else {
                                                                    ServerVectorIcon(server = server, tint = server.color, size = 14.dp)
                                                                }
                                                            }
                                                            Column(modifier = Modifier.weight(1f)) {
                                                                Text(
                                                                    text = server.name,
                                                                    fontSize = 12.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = if (isSelected) server.color else themeTextPrimary(),
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis,
                                                                    fontFamily = InterFont
                                                                )
                                                                if (server.badge.isNotBlank()) {
                                                                    Text(
                                                                        text = server.badge,
                                                                        fontSize = 9.sp,
                                                                        fontWeight = FontWeight.SemiBold,
                                                                        color = themeTextTertiary(),
                                                                        maxLines = 1,
                                                                        overflow = TextOverflow.Ellipsis,
                                                                        fontFamily = InterFont
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                    if (rowServers.size == 1) {
                                                        Spacer(Modifier.weight(1f))
                                                    }
                                                }
                                            }
                                        }

                                        if (filteredServers.size > collapsedCount) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .clickable {
                                                        isServerSelectorExpanded = !isServerSelectorExpanded
                                                        if (!isServerSelectorExpanded) serverSearchQuery = ""
                                                    }
                                                    .padding(vertical = 6.dp),
                                                horizontalArrangement = Arrangement.Center,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    if (isServerSelectorExpanded) "Show less" else "Show all ${filteredServers.size} servers",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = NexiRed,
                                                    fontFamily = InterFont
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                WatchSvgIcon(
                                                    type = if (isServerSelectorExpanded) "chevron_up" else "chevron_down",
                                                    tint = NexiRed,
                                                    size = 12.dp
                                                )
                                            }
                                        }
                                    }

                                    val activeServerObj = servers.find { if (selectedServerId != null) it.id == selectedServerId else it.url == selectedServerUrl }
                                    if (activeServerObj?.isHindi == true) {
                                        Text(
                                            "Tip: multi-audio server — switch to Hindi from the audio icon inside the player.",
                                            fontSize = 10.sp,
                                            lineHeight = 14.sp,
                                            color = themeTextTertiary(),
                                            fontFamily = InterFont
                                        )
                                    }
                                }
                            }

                            // ── Seasons & Episodes (numbered grid) ──
                            if (isSeries && state.seasons.isNotEmpty()) {
                                val selectedSeason = state.seasons.getOrNull(selectedSeasonIdx)
                                val allEpisodes = selectedSeason?.let { state.episodes[it.id] } ?: emptyList()
                                val episodes = remember(allEpisodes, episodeSearchQuery) {
                                    if (episodeSearchQuery.isBlank()) allEpisodes
                                    else {
                                        val q = episodeSearchQuery.trim().lowercase()
                                        allEpisodes.filter { ep ->
                                            ep.episodeNumber.toString().contains(q) ||
                                                (ep.episodeTitle?.lowercase()?.contains(q) == true)
                                        }
                                    }
                                }

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(themeCard(), RoundedCornerShape(16.dp))
                                        .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    WatchSectionHeader(
                                        icon = "folder",
                                        title = "Episodes",
                                        count = "${allEpisodes.size}"
                                    )

                                    if (state.seasons.size > 1) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            state.seasons.forEachIndexed { idx, season ->
                                                val sel = selectedSeasonIdx == idx
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .background(if (sel) NexiRed else themeBg())
                                                        .border(
                                                            1.dp,
                                                            if (sel) NexiRed else Color.White.copy(alpha = 0.06f),
                                                            RoundedCornerShape(10.dp)
                                                        )
                                                        .clickable {
                                                            selectedSeasonIdx = idx
                                                            selectedEpisodePageIdx = 0
                                                            episodeSearchQuery = ""
                                                        }
                                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                                ) {
                                                    Text(
                                                        "Season ${season.seasonNumber}",
                                                        fontSize = 12.sp,
                                                        fontWeight = if (sel) FontWeight.ExtraBold else FontWeight.SemiBold,
                                                        color = if (sel) Color.White else themeTextSecondary(),
                                                        fontFamily = InterFont
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (allEpisodes.size > 30) {
                                        OutlinedTextField(
                                            value = episodeSearchQuery,
                                            onValueChange = { episodeSearchQuery = it },
                                            placeholder = { Text("Jump to episode…", fontSize = 12.sp, color = themeTextTertiary()) },
                                            leadingIcon = { WatchSvgIcon(type = "search", tint = themeTextSecondary(), size = 15.dp) },
                                            trailingIcon = {
                                                if (episodeSearchQuery.isNotEmpty()) {
                                                    IconButton(onClick = { episodeSearchQuery = "" }) {
                                                        WatchSvgIcon(type = "close", tint = themeTextSecondary(), size = 13.dp)
                                                    }
                                                }
                                            },
                                            singleLine = true,
                                            shape = RoundedCornerShape(11.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = NexiRed,
                                                unfocusedBorderColor = Color.White.copy(alpha = 0.08f),
                                                focusedContainerColor = themeBg(),
                                                unfocusedContainerColor = themeBg(),
                                                focusedTextColor = themeTextPrimary(),
                                                unfocusedTextColor = themeTextPrimary()
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }

                                    if (episodes.isEmpty()) {
                                        Text(
                                            "No episodes found",
                                            color = themeTextTertiary(),
                                            fontSize = 13.sp,
                                            fontFamily = InterFont
                                        )
                                    } else {
                                        val pageChunkSize = 50
                                        val episodeChunks = remember(episodes) { episodes.chunked(pageChunkSize) }
                                        LaunchedEffect(selectedEpisode?.id, episodeChunks) {
                                            val activeId = selectedEpisode?.id ?: return@LaunchedEffect
                                            val foundChunkIdx = episodeChunks.indexOfFirst { chunk -> chunk.any { it.id == activeId } }
                                            if (foundChunkIdx >= 0 && foundChunkIdx != selectedEpisodePageIdx) {
                                                selectedEpisodePageIdx = foundChunkIdx
                                            }
                                        }
                                        val safeEpPageIdx = selectedEpisodePageIdx.coerceIn(0, (episodeChunks.size - 1).coerceAtLeast(0))
                                        val visibleEpisodes = if (episodeSearchQuery.isNotBlank()) {
                                            episodes.take(pageChunkSize)
                                        } else {
                                            episodeChunks.getOrNull(safeEpPageIdx) ?: emptyList()
                                        }

                                        if (episodeSearchQuery.isBlank() && episodeChunks.size > 1) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                episodeChunks.forEachIndexed { chunkIdx, chunk ->
                                                    val firstEpNum = chunk.firstOrNull()?.episodeNumber ?: (chunkIdx * pageChunkSize + 1)
                                                    val lastEpNum = chunk.lastOrNull()?.episodeNumber ?: ((chunkIdx + 1) * pageChunkSize)
                                                    val isChunkSelected = safeEpPageIdx == chunkIdx
                                                    Text(
                                                        "$firstEpNum–$lastEpNum",
                                                        fontSize = 11.sp,
                                                        fontWeight = if (isChunkSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                                        color = if (isChunkSelected) NexiRed else themeTextSecondary(),
                                                        fontFamily = InterFont,
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .background(if (isChunkSelected) NexiRed.copy(alpha = 0.15f) else Color.Transparent)
                                                            .border(
                                                                1.dp,
                                                                if (isChunkSelected) NexiRed.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.06f),
                                                                RoundedCornerShape(8.dp)
                                                            )
                                                            .clickable { selectedEpisodePageIdx = chunkIdx }
                                                            .padding(horizontal = 10.dp, vertical = 5.dp)
                                                    )
                                                }
                                            }
                                        }

                                        val gridColumns = 5
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            visibleEpisodes.chunked(gridColumns).forEach { rowEps ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    rowEps.forEach { ep ->
                                                        key(ep.id) {
                                                            val isSelected = selectedEpisode?.id == ep.id
                                                            val isWatched = watchedEpisodeIds.contains(ep.id)
                                                            Box(
                                                                modifier = Modifier
                                                                    .weight(1f)
                                                                    .aspectRatio(1.25f)
                                                                    .clip(RoundedCornerShape(10.dp))
                                                                    .background(
                                                                        when {
                                                                            isSelected -> NexiRed
                                                                            isWatched -> Color.White.copy(alpha = 0.04f)
                                                                            else -> themeBg()
                                                                        }
                                                                    )
                                                                    .border(
                                                                        1.dp,
                                                                        when {
                                                                            isSelected -> NexiRed
                                                                            else -> Color.White.copy(alpha = 0.07f)
                                                                        },
                                                                        RoundedCornerShape(10.dp)
                                                                    )
                                                                    .clickable {
                                                                        if (selectedEpisode?.id == ep.id) return@clickable
                                                                        selectedEpisode = ep
                                                                        markEpisodeWatched(ep.id)
                                                                        val sNum = state.seasons.getOrNull(selectedSeasonIdx)?.seasonNumber ?: 1
                                                                        val currentServers = getAvailableServers(
                                                                            type = effectiveType,
                                                                            streamingRow = state.streamingRow,
                                                                            movie = state.movie!!,
                                                                            episode = ep,
                                                                            seasonNumber = sNum
                                                                        )
                                                                        val matchedServer = currentServers.find { it.id == selectedServerId } ?: currentServers.firstOrNull()
                                                                        if (matchedServer != null) {
                                                                            activateAndResolveServer(matchedServer, sNum, ep.episodeNumber)
                                                                        }
                                                                    },
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                if (isSelected) {
                                                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                                        WatchSvgIcon(type = "play", tint = Color.White, size = 11.dp)
                                                                        Text(
                                                                            "${ep.episodeNumber}",
                                                                            fontSize = 13.sp,
                                                                            fontWeight = FontWeight.ExtraBold,
                                                                            color = Color.White,
                                                                            fontFamily = InterFont
                                                                        )
                                                                    }
                                                                } else {
                                                                    Text(
                                                                        "${ep.episodeNumber}",
                                                                        fontSize = 14.sp,
                                                                        fontWeight = FontWeight.Bold,
                                                                        color = if (isWatched) themeTextTertiary() else themeTextPrimary(),
                                                                        fontFamily = InterFont
                                                                    )
                                                                    if (isWatched) {
                                                                        Box(
                                                                            modifier = Modifier
                                                                                .align(Alignment.TopEnd)
                                                                                .padding(4.dp)
                                                                                .size(6.dp)
                                                                                .background(Color(0xFF10B981), CircleShape)
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                    repeat(gridColumns - rowEps.size) {
                                                        Spacer(Modifier.weight(1f))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // ── Content Overview Card (Web Watch Page Parity) ──
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(themeCard(), RoundedCornerShape(16.dp))
                                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .background(NexiRed.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        WatchSvgIcon(type = "info", tint = NexiRed, size = 14.dp)
                                    }
                                    Text(
                                        text = movie.title,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = themeTextPrimary(),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        fontFamily = InterFont,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = NexiRed.copy(alpha = 0.16f)
                                    ) {
                                        Text(
                                            text = effectiveType.uppercase(),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = NexiRed,
                                            fontFamily = InterFont,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                    movie.releaseYear?.let { yr ->
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = themeBg()
                                        ) {
                                            Text(
                                                text = "$yr",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = themeTextSecondary(),
                                                fontFamily = InterFont,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                    movie.language?.takeIf { it.isNotBlank() }?.let { lang ->
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFFF97316).copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = lang,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFFB923C),
                                                fontFamily = InterFont,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                    movie.format?.takeIf { it.isNotBlank() }?.let { fmt ->
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFF06B6D4).copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = fmt,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF22D3EE),
                                                fontFamily = InterFont,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }

                                if (state.categories.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        state.categories.forEach { cat ->
                                            Surface(
                                                shape = RoundedCornerShape(50),
                                                color = themeBg(),
                                                modifier = Modifier.border(
                                                    1.dp,
                                                    Color.White.copy(alpha = 0.06f),
                                                    RoundedCornerShape(50)
                                                )
                                            ) {
                                                Text(
                                                    text = cat.name,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = themeTextSecondary(),
                                                    fontFamily = InterFont,
                                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                movie.description?.takeIf { it.isNotBlank() }?.let { desc ->
                                    Text(
                                        text = desc,
                                        fontSize = 12.sp,
                                        lineHeight = 18.sp,
                                        color = themeTextSecondary(),
                                        maxLines = 4,
                                        overflow = TextOverflow.Ellipsis,
                                        fontFamily = InterFont
                                    )
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
                        if (customView != null && customView?.parent != frameLayout) {
                            (customView?.parent as? ViewGroup)?.removeView(customView)
                            frameLayout.removeAllViews()
                            frameLayout.addView(customView, FrameLayout.LayoutParams(
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

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun InlineWatchPlayer(
    streamUrl: String,
    onVideoEnded: () -> Unit,
    onOpenFullscreen: () -> Unit,
    onPlaybackError: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var currentPos by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var showControls by remember { mutableStateOf(true) }
    var audioTracks by remember { mutableStateOf<List<Triple<androidx.media3.common.TrackGroup, Int, String>>>(emptyList()) }
    var selectedAudioLabel by remember { mutableStateOf("Audio") }
    var showAudioDialog by remember { mutableStateOf(false) }
    var triedProxyFallback by remember(streamUrl) { mutableStateOf(false) }

    DisposableEffect(streamUrl) {
        val trackSelector = DefaultTrackSelector(context)
        val headers = resolveStreamHttpHeaders(streamUrl)
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)
            .setDefaultRequestProperties(headers)
        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(httpDataSourceFactory)

        val player = ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                setMediaItem(buildStreamMediaItem(streamUrl))
                prepare()
                playWhenReady = true
            }
        exoPlayer = player

        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlayerError(error: PlaybackException) {
                if (onPlaybackError != null) {
                    onPlaybackError.invoke()
                } else if (!triedProxyFallback && !streamUrl.contains("/api/proxy-stream", ignoreCase = true) && !streamUrl.contains("groovy.monster", ignoreCase = true)) {
                    triedProxyFallback = true
                    val proxiedUrl = "https://nexiplay.vercel.app/api/proxy-stream?url=${java.net.URLEncoder.encode(streamUrl, "UTF-8")}"
                    player.setMediaItem(buildStreamMediaItem(proxiedUrl))
                    player.prepare()
                    player.playWhenReady = true
                } else {
                    isBuffering = false
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    duration = player.duration.coerceAtLeast(0L)
                    val list = mutableListOf<Triple<androidx.media3.common.TrackGroup, Int, String>>()
                    var hindiSelected = false
                    for (group in player.currentTracks.groups) {
                        if (group.type == C.TRACK_TYPE_AUDIO) {
                            for (i in 0 until group.length) {
                                val fmt = group.getTrackFormat(i)
                                val label = fmt.label?.takeIf { it.isNotBlank() }
                                    ?: fmt.language?.takeIf { it.isNotBlank() }?.uppercase()
                                    ?: "Track ${list.size + 1}"
                                list.add(Triple(group.mediaTrackGroup, i, label))
                                if (!hindiSelected && (label.contains("hi", ignoreCase = true) || label.contains("hindi", ignoreCase = true))) {
                                    hindiSelected = true
                                    selectedAudioLabel = label
                                    if (!group.isTrackSelected(i)) {
                                        player.trackSelectionParameters = player.trackSelectionParameters
                                            .buildUpon()
                                            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i))
                                            .build()
                                    }
                                } else if (group.isTrackSelected(i) && !hindiSelected) {
                                    selectedAudioLabel = label
                                }
                            }
                        }
                    }
                    audioTracks = list
                } else if (state == Player.STATE_ENDED) {
                    onVideoEnded()
                }
            }
        }
        player.addListener(listener)

        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(isPlaying, showControls, exoPlayer) {
        exoPlayer?.let {
            currentPos = it.currentPosition.coerceAtLeast(0L)
            duration = it.duration.coerceAtLeast(0L)
        }
        while (showControls && isPlaying) {
            kotlinx.coroutines.delay(500)
            exoPlayer?.let {
                currentPos = it.currentPosition.coerceAtLeast(0L)
                duration = it.duration.coerceAtLeast(0L)
            }
        }
    }

    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            kotlinx.coroutines.delay(3500)
            showControls = false
        }
    }

    if (showAudioDialog && audioTracks.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showAudioDialog = false },
            containerColor = themeCard(),
            title = {
                Text("Select Audio Track", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = themeTextPrimary(), fontFamily = InterFont)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    audioTracks.forEach { (group, idx, label) ->
                        val active = selectedAudioLabel == label
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (active) NexiRed.copy(alpha = 0.2f) else themeBg(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    exoPlayer?.let { p ->
                                        p.trackSelectionParameters = p.trackSelectionParameters
                                            .buildUpon()
                                            .setOverrideForType(TrackSelectionOverride(group, idx))
                                            .build()
                                    }
                                    selectedAudioLabel = label
                                    showAudioDialog = false
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    WatchSvgIcon(
                                        type = "headphones",
                                        tint = if (active) NexiRed else themeTextSecondary(),
                                        size = 14.dp
                                    )
                                    Text(
                                        text = label,
                                        fontSize = 13.sp,
                                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                        color = if (active) NexiRed else themeTextPrimary(),
                                        fontFamily = InterFont
                                    )
                                }
                                if (active) {
                                    WatchSvgIcon(type = "check", tint = NexiRed, size = 14.dp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAudioDialog = false }) {
                    Text("Close", color = NexiRed, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { showControls = !showControls },
        contentAlignment = Alignment.Center
    ) {
        exoPlayer?.let { player ->
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { pv ->
                    if (pv.player != player) pv.player = player
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (isBuffering) {
            CircularProgressIndicator(color = NexiRed, modifier = Modifier.size(34.dp), strokeWidth = 3.dp)
        }

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
            ) {
                // Center Play/Pause & Skip Controls
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            exoPlayer?.let { it.seekTo((it.currentPosition - 10_000L).coerceAtLeast(0L)) }
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                    ) {
                        Icon(Icons.Default.Replay10, contentDescription = "-10s", tint = Color.White, modifier = Modifier.size(22.dp))
                    }

                    IconButton(
                        onClick = {
                            exoPlayer?.let {
                                if (it.isPlaying) it.pause() else it.play()
                            }
                        },
                        modifier = Modifier
                            .size(50.dp)
                            .background(NexiRed, CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            exoPlayer?.let {
                                val limit = if (it.duration > 0) it.duration else it.currentPosition + 10_000L
                                it.seekTo((it.currentPosition + 10_000L).coerceAtMost(limit))
                            }
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                    ) {
                        Icon(Icons.Default.Forward10, contentDescription = "+10s", tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                }

                // Bottom Bar: Seekbar + Time + Audio Track + Fullscreen Button
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Slider(
                        value = if (duration > 0L) (currentPos.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f,
                        onValueChange = { frac ->
                            if (duration > 0L) {
                                val target = (frac * duration).toLong()
                                currentPos = target
                                exoPlayer?.seekTo(target)
                            }
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = NexiRed,
                            activeTrackColor = NexiRed,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(20.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${formatInlineTime(currentPos)} / ${formatInlineTime(duration)}",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = InterFont
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (audioTracks.size > 1) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color.White.copy(alpha = 0.18f),
                                    modifier = Modifier.clickable { showAudioDialog = true }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Default.Language, contentDescription = "Audio", tint = Color.White, modifier = Modifier.size(13.dp))
                                        Text(selectedAudioLabel, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = InterFont)
                                    }
                                }
                            }

                            IconButton(
                                onClick = {
                                    exoPlayer?.pause()
                                    onOpenFullscreen()
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Fullscreen, contentDescription = "Fullscreen", tint = Color.White, modifier = Modifier.size(22.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatInlineTime(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSec = ms / 1000L
    val hrs = totalSec / 3600L
    val mins = (totalSec % 3600L) / 60L
    val secs = totalSec % 60L
    return if (hrs > 0L) {
        String.format("%d:%02d:%02d", hrs, mins, secs)
    } else {
        String.format("%02d:%02d", mins, secs)
    }
}

@Composable
private fun ServerVectorIcon(
    server: StreamServer,
    tint: Color,
    size: Dp = 16.dp
) {
    val iconType = when {
        server.id == "trailer" -> "trailer"
        server.id == "nexi_love" -> "heart"
        server.id == "circleftp" || server.badge.contains("BDIX", ignoreCase = true) -> "bolt"
        server.id in listOf("moviebox", "nexi_cinema") -> "cinema"
        server.id in listOf("miruro", "aniclover") -> "sparkle_4k"
        server.isHindi || server.id in listOf("twoembed", "vixsrc", "screenscape", "atlantic", "multiembed", "vidsrc_su", "anyembed", "animekhor", "rareanimes") -> "soundwave"
        server.id in listOf("ultra_4k", "nexi_frame", "cinesrc", "cineruv", "vexo", "nexi_joy", "nexi_nerds") || server.badge.contains("4K", ignoreCase = true) -> "sparkle_4k"
        server.id in listOf("toonplay", "animerulz", "custom", "rareanimes", "toonstream", "animixstream", "animeworld", "abyss", "streamtape", "doodstream", "voe", "godrive") -> "server_stack"
        server.badge.contains("FAST", ignoreCase = true) || server.id in listOf("luma_stream", "vidfast", "vidlink", "vidrock", "vidbolt", "vidcore", "videasy", "vidsrc_pm", "vidsrc_to", "vidapi", "nexi_core") -> "bolt"
        else -> "cinema"
    }
    WatchSvgIcon(type = iconType, tint = tint, size = size)
}

@Composable
private fun WatchSvgIcon(
    type: String,
    tint: Color,
    size: Dp = 16.dp,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(
            width = (w * 0.105f).coerceAtLeast(1.6f),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )

        when (type) {
            "back" -> {
                val p = Path().apply {
                    moveTo(w * 0.62f, h * 0.20f)
                    lineTo(w * 0.30f, h * 0.50f)
                    lineTo(w * 0.62f, h * 0.80f)
                }
                drawPath(p, color = tint, style = stroke)
            }
            "shield" -> {
                val p = Path().apply {
                    moveTo(w * 0.50f, h * 0.10f)
                    lineTo(w * 0.84f, h * 0.24f)
                    lineTo(w * 0.84f, h * 0.52f)
                    cubicTo(w * 0.84f, h * 0.74f, w * 0.68f, h * 0.86f, w * 0.50f, h * 0.92f)
                    cubicTo(w * 0.32f, h * 0.86f, w * 0.16f, h * 0.74f, w * 0.16f, h * 0.52f)
                    lineTo(w * 0.16f, h * 0.24f)
                    close()
                }
                drawPath(p, color = tint, style = stroke)
                val check = Path().apply {
                    moveTo(w * 0.36f, h * 0.52f)
                    lineTo(w * 0.46f, h * 0.62f)
                    lineTo(w * 0.66f, h * 0.40f)
                }
                drawPath(check, color = tint, style = stroke)
            }
            "prev_ep" -> {
                drawLine(
                    color = tint,
                    start = Offset(w * 0.22f, h * 0.22f),
                    end = Offset(w * 0.22f, h * 0.78f),
                    strokeWidth = stroke.width,
                    cap = StrokeCap.Round
                )
                val tri = Path().apply {
                    moveTo(w * 0.78f, h * 0.22f)
                    lineTo(w * 0.36f, h * 0.50f)
                    lineTo(w * 0.78f, h * 0.78f)
                    close()
                }
                drawPath(tri, color = tint)
            }
            "next_ep" -> {
                val tri = Path().apply {
                    moveTo(w * 0.22f, h * 0.22f)
                    lineTo(w * 0.64f, h * 0.50f)
                    lineTo(w * 0.22f, h * 0.78f)
                    close()
                }
                drawPath(tri, color = tint)
                drawLine(
                    color = tint,
                    start = Offset(w * 0.78f, h * 0.22f),
                    end = Offset(w * 0.78f, h * 0.78f),
                    strokeWidth = stroke.width,
                    cap = StrokeCap.Round
                )
            }
            "auto_next" -> {
                drawArc(
                    color = tint,
                    startAngle = -140f,
                    sweepAngle = 250f,
                    useCenter = false,
                    topLeft = Offset(w * 0.15f, h * 0.15f),
                    size = Size(w * 0.70f, h * 0.70f),
                    style = stroke
                )
                val play = Path().apply {
                    moveTo(w * 0.42f, h * 0.35f)
                    lineTo(w * 0.65f, h * 0.50f)
                    lineTo(w * 0.42f, h * 0.65f)
                    close()
                }
                drawPath(play, color = tint)
            }
            "refresh" -> {
                drawArc(
                    color = tint,
                    startAngle = -210f,
                    sweepAngle = 290f,
                    useCenter = false,
                    topLeft = Offset(w * 0.16f, h * 0.16f),
                    size = Size(w * 0.68f, h * 0.68f),
                    style = stroke
                )
                val arr = Path().apply {
                    moveTo(w * 0.44f, h * 0.08f)
                    lineTo(w * 0.68f, h * 0.16f)
                    lineTo(w * 0.50f, h * 0.38f)
                    close()
                }
                drawPath(arr, color = tint)
            }
            "fullscreen" -> {
                val d = w * 0.22f
                val p1 = Path().apply {
                    moveTo(w * 0.18f, h * 0.18f + d)
                    lineTo(w * 0.18f, h * 0.18f)
                    lineTo(w * 0.18f + d, h * 0.18f)
                }
                drawPath(p1, color = tint, style = stroke)
                val p2 = Path().apply {
                    moveTo(w * 0.82f - d, h * 0.18f)
                    lineTo(w * 0.82f, h * 0.18f)
                    lineTo(w * 0.82f, h * 0.18f + d)
                }
                drawPath(p2, color = tint, style = stroke)
                val p3 = Path().apply {
                    moveTo(w * 0.18f, h * 0.82f - d)
                    lineTo(w * 0.18f, h * 0.82f)
                    lineTo(w * 0.18f + d, h * 0.82f)
                }
                drawPath(p3, color = tint, style = stroke)
                val p4 = Path().apply {
                    moveTo(w * 0.82f - d, h * 0.82f)
                    lineTo(w * 0.82f, h * 0.82f)
                    lineTo(w * 0.82f, h * 0.82f - d)
                }
                drawPath(p4, color = tint, style = stroke)
            }
            "heart" -> {
                val p = Path().apply {
                    moveTo(w * 0.50f, h * 0.84f)
                    cubicTo(w * 0.20f, h * 0.62f, w * 0.10f, h * 0.45f, w * 0.10f, h * 0.32f)
                    cubicTo(w * 0.10f, h * 0.18f, w * 0.22f, h * 0.12f, w * 0.35f, h * 0.12f)
                    cubicTo(w * 0.43f, h * 0.12f, w * 0.48f, h * 0.18f, w * 0.50f, h * 0.24f)
                    cubicTo(w * 0.52f, h * 0.18f, w * 0.57f, h * 0.12f, w * 0.65f, h * 0.12f)
                    cubicTo(w * 0.78f, h * 0.12f, w * 0.90f, h * 0.18f, w * 0.90f, h * 0.32f)
                    cubicTo(w * 0.90f, h * 0.45f, w * 0.80f, h * 0.62f, w * 0.50f, h * 0.84f)
                    close()
                }
                drawPath(p, color = tint)
            }
            "switch_server" -> {
                drawLine(
                    color = tint,
                    start = Offset(w * 0.16f, h * 0.34f),
                    end = Offset(w * 0.80f, h * 0.34f),
                    strokeWidth = stroke.width,
                    cap = StrokeCap.Round
                )
                val topHead = Path().apply {
                    moveTo(w * 0.64f, h * 0.18f)
                    lineTo(w * 0.82f, h * 0.34f)
                    lineTo(w * 0.64f, h * 0.50f)
                }
                drawPath(topHead, color = tint, style = stroke)

                drawLine(
                    color = tint,
                    start = Offset(w * 0.84f, h * 0.66f),
                    end = Offset(w * 0.20f, h * 0.66f),
                    strokeWidth = stroke.width,
                    cap = StrokeCap.Round
                )
                val botHead = Path().apply {
                    moveTo(w * 0.36f, h * 0.50f)
                    lineTo(w * 0.18f, h * 0.66f)
                    lineTo(w * 0.36f, h * 0.82f)
                }
                drawPath(botHead, color = tint, style = stroke)
            }
            "download" -> {
                drawLine(
                    color = tint,
                    start = Offset(w * 0.50f, h * 0.15f),
                    end = Offset(w * 0.50f, h * 0.66f),
                    strokeWidth = stroke.width,
                    cap = StrokeCap.Round
                )
                val arrow = Path().apply {
                    moveTo(w * 0.30f, h * 0.48f)
                    lineTo(w * 0.50f, h * 0.68f)
                    lineTo(w * 0.70f, h * 0.48f)
                }
                drawPath(arrow, color = tint, style = stroke)
                val tray = Path().apply {
                    moveTo(w * 0.18f, h * 0.72f)
                    lineTo(w * 0.18f, h * 0.85f)
                    lineTo(w * 0.82f, h * 0.85f)
                    lineTo(w * 0.82f, h * 0.72f)
                }
                drawPath(tray, color = tint, style = stroke)
            }
            "chevron_down" -> {
                val p = Path().apply {
                    moveTo(w * 0.22f, h * 0.36f)
                    lineTo(w * 0.50f, h * 0.66f)
                    lineTo(w * 0.78f, h * 0.36f)
                }
                drawPath(p, color = tint, style = stroke)
            }
            "chevron_up" -> {
                val p = Path().apply {
                    moveTo(w * 0.22f, h * 0.64f)
                    lineTo(w * 0.50f, h * 0.34f)
                    lineTo(w * 0.78f, h * 0.64f)
                }
                drawPath(p, color = tint, style = stroke)
            }
            "layers" -> {
                val top = Path().apply {
                    moveTo(w * 0.50f, h * 0.14f)
                    lineTo(w * 0.88f, h * 0.34f)
                    lineTo(w * 0.50f, h * 0.54f)
                    lineTo(w * 0.12f, h * 0.34f)
                    close()
                }
                drawPath(top, color = tint, style = stroke)
                val mid = Path().apply {
                    moveTo(w * 0.14f, h * 0.54f)
                    lineTo(w * 0.50f, h * 0.72f)
                    lineTo(w * 0.86f, h * 0.54f)
                }
                drawPath(mid, color = tint, style = stroke)
                val bot = Path().apply {
                    moveTo(w * 0.14f, h * 0.72f)
                    lineTo(w * 0.50f, h * 0.90f)
                    lineTo(w * 0.86f, h * 0.72f)
                }
                drawPath(bot, color = tint, style = stroke)
            }
            "soundwave" -> {
                val bars = listOf(
                    0.18f to 0.26f,
                    0.34f to 0.54f,
                    0.50f to 0.78f,
                    0.66f to 0.48f,
                    0.82f to 0.28f
                )
                bars.forEach { (xFrac, hFrac) ->
                    val topY = h * (0.5f - hFrac / 2f)
                    val botY = h * (0.5f + hFrac / 2f)
                    drawLine(
                        color = tint,
                        start = Offset(w * xFrac, topY),
                        end = Offset(w * xFrac, botY),
                        strokeWidth = stroke.width,
                        cap = StrokeCap.Round
                    )
                }
            }
            "bolt" -> {
                val p = Path().apply {
                    moveTo(w * 0.56f, h * 0.10f)
                    lineTo(w * 0.20f, h * 0.54f)
                    lineTo(w * 0.48f, h * 0.54f)
                    lineTo(w * 0.42f, h * 0.90f)
                    lineTo(w * 0.80f, h * 0.44f)
                    lineTo(w * 0.52f, h * 0.44f)
                    close()
                }
                drawPath(p, color = tint)
            }
            "headphones" -> {
                drawArc(
                    color = tint,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(w * 0.16f, h * 0.16f),
                    size = Size(w * 0.68f, h * 0.64f),
                    style = stroke
                )
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.12f, h * 0.50f),
                    size = Size(w * 0.18f, h * 0.34f),
                    cornerRadius = CornerRadius(w * 0.06f, w * 0.06f)
                )
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.70f, h * 0.50f),
                    size = Size(w * 0.18f, h * 0.34f),
                    cornerRadius = CornerRadius(w * 0.06f, w * 0.06f)
                )
            }
            "crown" -> {
                val p = Path().apply {
                    moveTo(w * 0.14f, h * 0.78f)
                    lineTo(w * 0.22f, h * 0.30f)
                    lineTo(w * 0.40f, h * 0.52f)
                    lineTo(w * 0.50f, h * 0.22f)
                    lineTo(w * 0.60f, h * 0.52f)
                    lineTo(w * 0.78f, h * 0.30f)
                    lineTo(w * 0.86f, h * 0.78f)
                    close()
                }
                drawPath(p, color = tint)
            }
            "check" -> {
                val p = Path().apply {
                    moveTo(w * 0.20f, h * 0.52f)
                    lineTo(w * 0.42f, h * 0.74f)
                    lineTo(w * 0.82f, h * 0.28f)
                }
                drawPath(p, color = tint, style = stroke)
            }
            "search" -> {
                drawCircle(
                    color = tint,
                    radius = w * 0.28f,
                    center = Offset(w * 0.44f, h * 0.44f),
                    style = stroke
                )
                drawLine(
                    color = tint,
                    start = Offset(w * 0.65f, h * 0.65f),
                    end = Offset(w * 0.86f, h * 0.86f),
                    strokeWidth = stroke.width,
                    cap = StrokeCap.Round
                )
            }
            "close" -> {
                drawLine(
                    color = tint,
                    start = Offset(w * 0.24f, h * 0.24f),
                    end = Offset(w * 0.76f, h * 0.76f),
                    strokeWidth = stroke.width,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = tint,
                    start = Offset(w * 0.76f, h * 0.24f),
                    end = Offset(w * 0.24f, h * 0.76f),
                    strokeWidth = stroke.width,
                    cap = StrokeCap.Round
                )
            }
            "play" -> {
                val p = Path().apply {
                    moveTo(w * 0.26f, h * 0.18f)
                    lineTo(w * 0.82f, h * 0.50f)
                    lineTo(w * 0.26f, h * 0.82f)
                    close()
                }
                drawPath(p, color = tint)
            }
            "folder" -> {
                val p = Path().apply {
                    moveTo(w * 0.12f, h * 0.24f)
                    lineTo(w * 0.40f, h * 0.24f)
                    lineTo(w * 0.48f, h * 0.34f)
                    lineTo(w * 0.88f, h * 0.34f)
                    lineTo(w * 0.88f, h * 0.80f)
                    lineTo(w * 0.12f, h * 0.80f)
                    close()
                }
                drawPath(p, color = tint, style = stroke)
            }
            "info" -> {
                drawCircle(
                    color = tint,
                    radius = w * 0.38f,
                    center = Offset(w * 0.50f, h * 0.50f),
                    style = stroke
                )
                drawCircle(
                    color = tint,
                    radius = w * 0.06f,
                    center = Offset(w * 0.50f, h * 0.32f)
                )
                drawLine(
                    color = tint,
                    start = Offset(w * 0.50f, h * 0.46f),
                    end = Offset(w * 0.50f, h * 0.70f),
                    strokeWidth = stroke.width,
                    cap = StrokeCap.Round
                )
            }
            "server_stack" -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.14f, h * 0.16f),
                    size = Size(w * 0.72f, h * 0.28f),
                    cornerRadius = CornerRadius(w * 0.07f, w * 0.07f),
                    style = stroke
                )
                drawCircle(color = tint, radius = w * 0.05f, center = Offset(w * 0.28f, h * 0.30f))
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.14f, h * 0.56f),
                    size = Size(w * 0.72f, h * 0.28f),
                    cornerRadius = CornerRadius(w * 0.07f, w * 0.07f),
                    style = stroke
                )
                drawCircle(color = tint, radius = w * 0.05f, center = Offset(w * 0.28f, h * 0.70f))
            }
            "sparkle_4k" -> {
                val p = Path().apply {
                    moveTo(w * 0.50f, h * 0.10f)
                    lineTo(w * 0.62f, h * 0.38f)
                    lineTo(w * 0.90f, h * 0.50f)
                    lineTo(w * 0.62f, h * 0.62f)
                    lineTo(w * 0.50f, h * 0.90f)
                    lineTo(w * 0.38f, h * 0.62f)
                    lineTo(w * 0.10f, h * 0.50f)
                    lineTo(w * 0.38f, h * 0.38f)
                    close()
                }
                drawPath(p, color = tint)
            }
            "trailer" -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.12f, h * 0.22f),
                    size = Size(w * 0.76f, h * 0.56f),
                    cornerRadius = CornerRadius(w * 0.10f, w * 0.10f),
                    style = stroke
                )
                val tri = Path().apply {
                    moveTo(w * 0.42f, h * 0.36f)
                    lineTo(w * 0.64f, h * 0.50f)
                    lineTo(w * 0.42f, h * 0.64f)
                    close()
                }
                drawPath(tri, color = tint)
            }
            else -> { // "cinema"
                drawCircle(
                    color = tint,
                    radius = w * 0.38f,
                    center = Offset(w * 0.50f, h * 0.50f),
                    style = stroke
                )
                val tri = Path().apply {
                    moveTo(w * 0.42f, h * 0.34f)
                    lineTo(w * 0.68f, h * 0.50f)
                    lineTo(w * 0.42f, h * 0.66f)
                    close()
                }
                drawPath(tri, color = tint)
            }
        }
    }
}

@Composable
private fun WatchActionButton(
    icon: String,
    label: String,
    tint: Color,
    highlighted: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (highlighted) tint.copy(alpha = 0.12f) else themeBg(),
        modifier = modifier
            .border(
                1.dp,
                if (highlighted) tint.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(10.dp)
            )
            .bounceClick { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            WatchSvgIcon(type = icon, tint = tint, size = 15.dp)
            Spacer(Modifier.height(3.dp))
            Text(
                label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontFamily = InterFont
            )
        }
    }
}

@Composable
private fun WatchSectionHeader(
    icon: String,
    title: String,
    count: String? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .background(NexiRed.copy(alpha = 0.14f), RoundedCornerShape(7.dp)),
            contentAlignment = Alignment.Center
        ) {
            WatchSvgIcon(type = icon, tint = NexiRed, size = 13.dp)
        }
        Text(
            title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = themeTextPrimary(),
            fontFamily = InterFont
        )
        if (count != null) {
            Text(
                "($count)",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = themeTextTertiary(),
                fontFamily = InterFont
            )
        }
    }
}
