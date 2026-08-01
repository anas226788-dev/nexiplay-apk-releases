package com.nexiplay.app.ui.screens.detail

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.nexiplay.app.data.repository.DownloadRepository
import com.nexiplay.app.data.repository.SettingsRepository
import com.nexiplay.app.data.repository.DownloadService
import com.nexiplay.app.ui.theme.themeBg
import com.nexiplay.app.ui.theme.themeSurface
import com.nexiplay.app.ui.theme.NexiRed
import com.nexiplay.app.ui.theme.themeTextPrimary
import java.io.ByteArrayInputStream
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

// ── Ad keywords in host ──
private val AD_HOST_KEYWORDS = listOf(
    "doubleclick", "googlesyndication", "googleadservices", "google-analytics",
    "googletagmanager", "googletagservices", "adservice",
    "adserv", "adtrack", "advert", "adsystem", "adserver", "adnetwork",
    "popunder", "popcash", "popads", "propeller",
    "trafficjunky", "exoclick", "juicyads", "clickadu", "hilltopads",
    "monetag", "adsterra", "pushame", "pushengage", "richpush",
    "taboola", "outbrain", "mgid", "revcontent",
    "criteo", "moatads", "quantserve", "scorecardresearch",
    "pubmatic", "openx", "rubiconproject", "smartadserver",
    "casalemedia", "contextweb", "sharethrough",
    "mopub", "applovin", "vungle", "chartboost", "inmobi",
    "startapp", "leadbolt", "smaato", "tapjoy", "admob",
    "bidvertiser", "adcolony", "adform", "serving-sys",
    "tsyndicate", "setupad", "ezoic", "adthrive", "mediavine",
    "snigel", "sovrn", "teads", "spotx", "connatix", "vidoomy",
    "adtelligent", "flashtalking", "doubleverify", "adsafeprotected",
    "amazon-adsystem", "media.net", "bluekai",
    "hotjar", "fullstory", "crazyegg", "mouseflow", "luckyorange",
    "mixpanel", "onesignal", "cleverpush", "webpushr", "gravitec",
    "izooto", "sendpulse", "pushwoosh", "pushnami",
    "exosrv", "plugrush", "zedo", "disqusads",
    "kiosked", "springserve", "cedato"
)

private val AD_PATH_KEYWORDS = listOf(
    "/ads/", "/ad/", "/adserv", "/banner/", "/popup",
    "/pagead/", "/aclk?", "google_ads", "amazon_ads", "prebid", "adsense"
)

// Trusted download sites where ad blocking should be disabled
private val TRUSTED_DOWNLOAD_HOSTS = listOf(
    "mega.nz", "mega.co.nz", "mega.io",
    "pixeldrain.com",
    "drive.google.com", "docs.google.com",
    "mediafire.com", "www.mediafire.com",
    "terabox.com", "www.terabox.com",
    "pcloud.link", "www.pcloud.com",
    "youtube.com", "www.youtube.com"
)

private fun isTrustedSite(url: String): Boolean {
    val host = try { java.net.URI(url.lowercase()).host ?: "" } catch (_: Exception) { "" }
    return TRUSTED_DOWNLOAD_HOSTS.any { host.contains(it) }
}

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
    window.onbeforeunload = null;
})();
"""

// ── Helper: start download using system DownloadManager directly ──
private fun startSystemDownload(context: Context, downloadUrl: String, fileName: String, title: String) {
    try {
        val settings = SettingsRepository(context)
        if (settings.sdCardUri != null) {
            // Use custom download service for SAF SD Card downloads
            DownloadService.start(context, downloadUrl, fileName, title)
            Toast.makeText(context, "Download started to SD Card: $title", Toast.LENGTH_SHORT).show()
            
            // Save dummy record so it appears in history (we use -1 for downloadManagerId since we bypass it)
            val repo = DownloadRepository(context)
            repo.saveDownloadRecord(-1L, title, downloadUrl)
            return
        }

        val cookie = CookieManager.getInstance().getCookie(downloadUrl)
        val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
            setTitle(title)
            setDescription("Downloading via NexiPlay")
            addRequestHeader("Cookie", cookie ?: "")
            addRequestHeader("User-Agent", WebView(context).settings.userAgentString)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "NexiPlay/$fileName")
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = dm.enqueue(request)

        // Save to our download history
        val repo = DownloadRepository(context)
        repo.saveDownloadRecord(downloadId, title, downloadUrl)

        Toast.makeText(context, "⬇ Download started: $title", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "❌ Download failed: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebViewScreen(navController: NavController, encodedUrl: String) {
    val url = remember { URLDecoder.decode(encodedUrl, StandardCharsets.UTF_8.toString()) }
    var pageTitle by remember { mutableStateOf("Loading...") }
    var isLoading by remember { mutableStateOf(true) }
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    val context = LocalContext.current

    // ── Pending download info (URL, filename) ──
    var pendingDownloadUrl by remember { mutableStateOf<String?>(null) }
    var pendingDownloadName by remember { mutableStateOf<String?>(null) }

    // ── Permission launcher ──
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Whether granted or not, try the download anyway
        // DownloadManager works without WRITE_EXTERNAL_STORAGE on Android 10+
        val dlUrl = pendingDownloadUrl
        val dlName = pendingDownloadName
        if (dlUrl != null && dlName != null) {
            startSystemDownload(context, dlUrl, dlName, dlName)
        }
        pendingDownloadUrl = null
        pendingDownloadName = null
    }

    // Function to request permissions and start download
    fun downloadWithPermission(downloadUrl: String, fileName: String) {
        val permissionsNeeded = mutableListOf<String>()

        // Notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Storage permission for Android 9 and below
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        if (permissionsNeeded.isNotEmpty()) {
            pendingDownloadUrl = downloadUrl
            pendingDownloadName = fileName
            permissionLauncher.launch(permissionsNeeded.toTypedArray())
        } else {
            startSystemDownload(context, downloadUrl, fileName, fileName)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(pageTitle, maxLines = 1, fontSize = 14.sp, color = themeTextPrimary()) },
                navigationIcon = {
                    IconButton(onClick = {
                        val wv = webViewRef.value
                        if (wv != null && wv.canGoBack()) {
                            wv.goBack()
                        } else {
                            navController.popBackStack()
                        }
                    }) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = themeTextPrimary())
                    }
                },
                actions = {
                    IconButton(onClick = { webViewRef.value?.reload() }) {
                        Icon(Icons.Default.Refresh, "Reload", tint = themeTextPrimary())
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = themeSurface())
            )
        },
        containerColor = themeBg()
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        val isTrusted = isTrustedSite(url)
                        
                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(
                                view: WebView?, request: WebResourceRequest?
                            ): WebResourceResponse? {
                                if (isTrusted) return super.shouldInterceptRequest(view, request)
                                val requestUrl = request?.url?.toString() ?: return null
                                if (isAdUrl(requestUrl)) return EMPTY_RESPONSE
                                return super.shouldInterceptRequest(view, request)
                            }

                            override fun onPageStarted(view: WebView?, loadUrl: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, loadUrl, favicon)
                                isLoading = true
                            }

                            override fun onPageFinished(view: WebView?, loadUrl: String?) {
                                super.onPageFinished(view, loadUrl)
                                isLoading = false
                                if (!isTrusted) {
                                    view?.evaluateJavascript(AD_HIDE_JS, null)
                                }
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView?, request: WebResourceRequest?
                            ): Boolean {
                                val reqUrl = request?.url?.toString() ?: return false
                                if (!isTrusted && isAdUrl(reqUrl)) return true
                                return false
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                super.onReceivedTitle(view, title)
                                if (!title.isNullOrBlank()) pageTitle = title
                            }

                            override fun onCreateWindow(
                                view: WebView?, isDialog: Boolean,
                                isUserGesture: Boolean, resultMsg: android.os.Message?
                            ): Boolean {
                                if (view == null) return false
                                // Extract href from the clicked element
                                val result = view.hitTestResult
                                val data = result.extra
                                if (data != null && !isAdUrl(data)) {
                                    view.loadUrl(data)
                                }
                                // Also handle via transport
                                val tempWebView = WebView(view.context)
                                tempWebView.webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        v: WebView?, request: WebResourceRequest?
                                    ): Boolean {
                                        val newUrl = request?.url?.toString() ?: return false
                                        if (!isAdUrl(newUrl)) {
                                            view.loadUrl(newUrl)
                                        }
                                        try { tempWebView.destroy() } catch (_: Exception) {}
                                        return true
                                    }
                                }
                                tempWebView.setDownloadListener { dlUrl, _, cd, mime, _ ->
                                    val name = URLUtil.guessFileName(dlUrl, cd, mime)
                                    downloadWithPermission(dlUrl, name)
                                    try { tempWebView.destroy() } catch (_: Exception) {}
                                }
                                val transport = resultMsg?.obj as? android.webkit.WebView.WebViewTransport
                                transport?.webView = tempWebView
                                resultMsg?.sendToTarget()
                                return true
                            }
                        }

                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.setSupportMultipleWindows(true)
                        settings.javaScriptCanOpenWindowsAutomatically = true
                        settings.setSupportZoom(true)
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.allowFileAccess = true
                        settings.allowContentAccess = true

                        // Main download listener
                        setDownloadListener { dlUrl, userAgent, contentDisposition, mimeType, _ ->
                            val fileName = URLUtil.guessFileName(dlUrl, contentDisposition, mimeType)
                            downloadWithPermission(dlUrl, fileName)
                        }

                        loadUrl(url)
                        webViewRef.value = this
                    }
                },
                update = { wv -> webViewRef.value = wv },
                modifier = Modifier.fillMaxSize()
            )

            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                    color = NexiRed
                )
            }
        }
    }

    // System back button
    androidx.activity.compose.BackHandler(enabled = true) {
        val wv = webViewRef.value
        if (wv != null && wv.canGoBack()) {
            wv.goBack()
        } else {
            navController.popBackStack()
        }
    }
}
