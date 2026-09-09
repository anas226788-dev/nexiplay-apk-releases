package com.nexiplay.app.ui.screens.detail

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.material.icons.filled.OpenInBrowser
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

// ── Specific known ad networks & tracking domains for sub-resource filtering only ──
private val KNOWN_AD_DOMAINS = listOf(
    "doubleclick.net", "googlesyndication.com", "googleadservices.com",
    "adsterra.com", "monetag.com", "propellerads.com", "highcpmgate.com",
    "effectivecpmgate.com", "alwingulla.com", "thoufaud.com", "onclickalgo.com",
    "poawoopt.com", "asewt.com", "wpadmngr.com", "exoclick.com", "trafficjunky.com",
    "clickadu.com", "hilltopads.com", "popads.net", "popcash.net", "admaven.com",
    "richpush.com", "notix.co", "pushame.com", "pushengage.com"
)

private fun isKnownAdHost(url: String): Boolean {
    val host = try { java.net.URI(url.lowercase()).host ?: "" } catch (_: Exception) { "" }
    return KNOWN_AD_DOMAINS.any { host == it || host.endsWith(".$it") }
}

// Check ONLY genuine direct media, archive, and apk file streams
private fun isDirectDownloadUrl(url: String): Boolean {
    val cleanUrl = url.substringBefore("?").substringBefore("#").lowercase()
    val fullLower = url.lowercase()

    // 1. Direct file stream endpoints
    if (fullLower.contains("drive.google.com/uc?") && fullLower.contains("export=download")) return true
    if (fullLower.contains("docs.google.com/uc?") && fullLower.contains("export=download")) return true
    if (fullLower.contains("pixeldrain.com/api/file/")) return true

    // 2. Direct file extensions on URL path
    val mediaExtensions = listOf(
        ".mp4", ".mkv", ".avi", ".mov", ".wmv", ".webm", ".flv",
        ".zip", ".rar", ".7z", ".tar", ".gz", ".apk", ".iso", ".srt"
    )
    return mediaExtensions.any { cleanUrl.endsWith(it) }
}

private val EMPTY_RESPONSE = WebResourceResponse(
    "text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0))
)

// Smart JS to remove full-screen invisible clickjack overlays, convert target="_blank" to direct links, and route window.open cleanly
private const val ANTI_CLICKJACK_JS = """
(function() {
    window.onbeforeunload = null;
    
    // Convert all target="_blank" and target="_new" so the new download site opens right inside this WebView!
    function fixLinksAndWindows() {
        var links = document.querySelectorAll('a[target="_blank"], a[target="_new"]');
        links.forEach(function(link) {
            link.setAttribute('target', '_self');
        });
    }

    // Intercept window.open so JS click handlers (like Instant DL) navigate smoothly in current window
    window.open = function(url, target, features) {
        if (url && typeof url === 'string') {
            window.location.href = url;
        }
        return window;
    };
    
    // Remove only transparent invisible clickjack layers (do NOT hide real buttons!)
    function removeOverlays() {
        var elements = document.querySelectorAll('div, a, iframe, span, section');
        elements.forEach(function(el) {
            var style = window.getComputedStyle(el);
            if ((style.position === 'fixed' || style.position === 'absolute') && 
                (parseInt(style.zIndex) > 1000 || style.zIndex === '999999' || style.zIndex === '2147483647')) {
                var w = el.offsetWidth;
                var h = el.offsetHeight;
                if (w > window.innerWidth * 0.8 && h > window.innerHeight * 0.8) {
                    if (parseFloat(style.opacity) < 0.1 || style.backgroundColor === 'transparent' || style.backgroundColor === 'rgba(0, 0, 0, 0)') {
                        el.remove();
                    }
                }
            }
        });
    }
    
    setInterval(function() {
        fixLinksAndWindows();
        removeOverlays();
    }, 500);
    
    fixLinksAndWindows();
    removeOverlays();
})();
"""

// ── Helper: start download using system DownloadManager directly ──
private fun startSystemDownload(context: Context, downloadUrl: String, fileName: String, title: String) {
    try {
        // Prevent downloading garbage .bin files
        var safeFileName = fileName
        if (safeFileName.endsWith(".bin") || safeFileName.isBlank()) {
            safeFileName = "video_${System.currentTimeMillis()}.mp4"
        }

        val settings = SettingsRepository(context)
        if (settings.sdCardUri != null) {
            // Use custom download service for SAF SD Card downloads
            DownloadService.start(context, downloadUrl, safeFileName, title)
            Toast.makeText(context, "Download started to SD Card: $title", Toast.LENGTH_SHORT).show()
            
            val repo = DownloadRepository(context)
            repo.saveDownloadRecord(-1L, title, downloadUrl)
            return
        }

        val cookie = CookieManager.getInstance().getCookie(downloadUrl)
        val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
            setTitle(title)
            setDescription("Downloading via NexiPlay")
            addRequestHeader("Cookie", cookie ?: "")
            addRequestHeader("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36")
            addRequestHeader("Referer", downloadUrl)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "NexiPlay/$safeFileName")
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = dm.enqueue(request)

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
    val initialUrl = remember { URLDecoder.decode(encodedUrl, StandardCharsets.UTF_8.toString()) }
    var currentUrl by remember { mutableStateOf(initialUrl) }
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
    ) { _ ->
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

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

    // Helper to route new window URLs into download or navigation
    fun handleNewWindowUrl(mainView: WebView?, targetUrl: String) {
        if (mainView == null || targetUrl.isBlank()) return
        if (isDirectDownloadUrl(targetUrl)) {
            val fileName = URLUtil.guessFileName(targetUrl, null, null)
            downloadWithPermission(targetUrl, fileName)
        } else {
            mainView.loadUrl(targetUrl)
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
                            val list = wv.copyBackForwardList()
                            val currentIndex = list.currentIndex
                            var targetIndex = currentIndex - 1
                            while (targetIndex >= 0) {
                                val item = list.getItemAtIndex(targetIndex)
                                val itemUrl = item.url.lowercase()
                                if (!isKnownAdHost(itemUrl)) {
                                    val steps = targetIndex - currentIndex
                                    wv.goBackOrForward(steps)
                                    return@IconButton
                                }
                                targetIndex--
                            }
                            wv.goBack()
                        } else {
                            navController.popBackStack()
                        }
                    }) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = themeTextPrimary())
                    }
                },
                actions = {
                    // Open in Chrome browser fallback button
                    IconButton(onClick = {
                        val activeUrl = webViewRef.value?.url ?: currentUrl
                        try {
                            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(activeUrl))
                            context.startActivity(browserIntent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Cannot open browser: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Default.OpenInBrowser, "Open in Chrome", tint = themeTextPrimary())
                    }

                    // Reload button
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
                        // Accept cookies & 3rd-party cookies for GDrive download verification
                        val cookieManager = CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        cookieManager.setAcceptThirdPartyCookies(this, true)

                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(
                                view: WebView?, request: WebResourceRequest?
                            ): WebResourceResponse? {
                                val requestUrl = request?.url?.toString() ?: return null
                                // ONLY block 3rd-party ad scripts/iframes, NEVER block the main page!
                                if (request.isForMainFrame == false && isKnownAdHost(requestUrl)) {
                                    return EMPTY_RESPONSE
                                }
                                return super.shouldInterceptRequest(view, request)
                            }

                            override fun onPageStarted(view: WebView?, loadUrl: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, loadUrl, favicon)
                                isLoading = true
                                if (loadUrl != null) currentUrl = loadUrl
                            }

                            override fun onPageFinished(view: WebView?, loadUrl: String?) {
                                super.onPageFinished(view, loadUrl)
                                isLoading = false
                                if (loadUrl != null) currentUrl = loadUrl
                                view?.evaluateJavascript(ANTI_CLICKJACK_JS, null)
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView?, request: WebResourceRequest?
                            ): Boolean {
                                val reqUrl = request?.url?.toString() ?: return false

                                // Intercept ONLY genuine direct media/archive files
                                if (isDirectDownloadUrl(reqUrl)) {
                                    var fileName = URLUtil.guessFileName(reqUrl, null, null)
                                    if (fileName.endsWith(".bin") || fileName.isBlank()) {
                                        fileName = "video_${System.currentTimeMillis()}.mp4"
                                    }
                                    downloadWithPermission(reqUrl, fileName)
                                    return true
                                }

                                // Handle intent:// without throwing out to Chrome
                                if (reqUrl.startsWith("intent://")) {
                                    try {
                                        val intent = Intent.parseUri(reqUrl, Intent.URI_INTENT_SCHEME)
                                        if (intent != null) {
                                            val fallbackUrl = intent.getStringExtra("browser_fallback_url")
                                            if (fallbackUrl != null) {
                                                view?.loadUrl(fallbackUrl)
                                                return true
                                            }
                                        }
                                    } catch (_: Exception) {}
                                    return true // Block external launch to Chrome
                                }

                                // Block market:// or play.google.com from ad redirects
                                if (reqUrl.startsWith("market://") || reqUrl.lowercase().contains("play.google.com/store")) {
                                    return true
                                }

                                // For standard non-http custom schemes (e.g. tg://, vlc://)
                                if (!reqUrl.startsWith("http://") && !reqUrl.startsWith("https://") &&
                                    !reqUrl.startsWith("about:") && !reqUrl.startsWith("javascript:")) {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(reqUrl))
                                        context.startActivity(intent)
                                    } catch (_: Exception) {}
                                    return true
                                }

                                // ALLOW all normal webpage navigations so Instant DL target sites load cleanly!
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

                                // Check hit test result directly for immediate click handling
                                val hitResult = view.hitTestResult
                                val hitUrl = hitResult.extra
                                if (!hitUrl.isNullOrBlank()) {
                                    handleNewWindowUrl(view, hitUrl)
                                    return false
                                }

                                // Temporary WebView to resolve dynamic JavaScript target URL safely
                                val tempWebView = WebView(view.context).apply {
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.userAgentString = view.settings.userAgentString
                                }

                                tempWebView.webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        v: WebView?, request: WebResourceRequest?
                                    ): Boolean {
                                        val targetUrl = request?.url?.toString() ?: return false
                                        handleNewWindowUrl(view, targetUrl)
                                        try { tempWebView.destroy() } catch (_: Exception) {}
                                        return true
                                    }

                                    override fun onPageStarted(v: WebView?, targetUrl: String?, favicon: Bitmap?) {
                                        super.onPageStarted(v, targetUrl, favicon)
                                        if (!targetUrl.isNullOrBlank() && targetUrl != "about:blank") {
                                            handleNewWindowUrl(view, targetUrl)
                                            try { tempWebView.destroy() } catch (_: Exception) {}
                                        }
                                    }
                                }

                                tempWebView.setDownloadListener { dlUrl, _, cd, mime, _ ->
                                    var name = URLUtil.guessFileName(dlUrl, cd, mime)
                                    if (name.endsWith(".bin") || name.isBlank()) {
                                        name = "video_${System.currentTimeMillis()}.mp4"
                                    }
                                    downloadWithPermission(dlUrl, name)
                                    try { tempWebView.destroy() } catch (_: Exception) {}
                                }

                                val transport = resultMsg?.obj as? WebView.WebViewTransport
                                transport?.webView = tempWebView
                                resultMsg?.sendToTarget()
                                return true
                            }
                        }

                        // Use modern mobile Chrome User-Agent for seamless GDrive & Cloudflare compatibility
                        settings.userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
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

                        // Main download listener for server-sent attachments
                        setDownloadListener { dlUrl, userAgent, contentDisposition, mimeType, _ ->
                            var fileName = URLUtil.guessFileName(dlUrl, contentDisposition, mimeType)
                            if (fileName.endsWith(".bin") || fileName.isBlank()) {
                                fileName = "video_${System.currentTimeMillis()}.mp4"
                            }
                            downloadWithPermission(dlUrl, fileName)
                        }

                        loadUrl(initialUrl)
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

    // Chrome-like Back Button Navigation: skips any ad redirect loops and takes user straight back to previous download page
    androidx.activity.compose.BackHandler(enabled = true) {
        val wv = webViewRef.value
        if (wv != null && wv.canGoBack()) {
            val list = wv.copyBackForwardList()
            val currentIndex = list.currentIndex
            var targetIndex = currentIndex - 1
            while (targetIndex >= 0) {
                val item = list.getItemAtIndex(targetIndex)
                val itemUrl = item.url.lowercase()
                if (!isKnownAdHost(itemUrl)) {
                    val steps = targetIndex - currentIndex
                    wv.goBackOrForward(steps)
                    return@BackHandler
                }
                targetIndex--
            }
            wv.goBack()
        } else {
            navController.popBackStack()
        }
    }
}
