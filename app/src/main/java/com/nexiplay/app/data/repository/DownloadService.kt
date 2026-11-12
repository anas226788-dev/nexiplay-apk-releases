package com.nexiplay.app.data.repository

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaScannerConnection
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.PowerManager
import android.os.StatFs
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import com.nexiplay.app.MainActivity
import com.nexiplay.app.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import okhttp3.Call
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.URI
import java.util.Collections
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

class DownloadService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val downloadSemaphore = Semaphore(2)
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val activeCalls = ConcurrentHashMap<String, Call>()
    private val pauseRequested = Collections.synchronizedSet(mutableSetOf<String>())
    private val cancelRequested = Collections.synchronizedSet(mutableSetOf<String>())

    private val lockMutex = Any()
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    @Volatile
    private var lastNotifUpdateMs = 0L

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    private val appIconBitmap: android.graphics.Bitmap? by lazy {
        try {
            android.graphics.BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        const val CHANNEL_ACTIVE = "nexiplay_downloads_active"
        const val CHANNEL_ALERTS = "nexiplay_downloads_alerts"
        private const val FOREGROUND_NOTIF_ID = 9001

        const val ACTION_ENQUEUE_DIRECT = "com.nexiplay.app.dl.ENQUEUE_DIRECT"
        const val ACTION_ENQUEUE_VAULT = "com.nexiplay.app.dl.ENQUEUE_VAULT"
        const val ACTION_PAUSE = "com.nexiplay.app.dl.PAUSE"
        const val ACTION_RESUME = "com.nexiplay.app.dl.RESUME"
        const val ACTION_RETRY = "com.nexiplay.app.dl.RETRY"
        const val ACTION_CANCEL = "com.nexiplay.app.dl.CANCEL"
        const val ACTION_PAUSE_ALL = "com.nexiplay.app.dl.PAUSE_ALL"
        const val ACTION_RESUME_ALL = "com.nexiplay.app.dl.RESUME_ALL"

        const val EXTRA_TASK_ID = "task_id"

        private const val DEFAULT_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

        fun getPartsDir(context: Context): File {
            val base = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
            return File(base, ".nexi_parts").apply { if (!exists()) mkdirs() }
        }

        // Backwards-compatible helper
        fun start(context: Context, url: String, fileName: String, title: String) {
            DownloadRepository(context).enqueueDirectDownload(url = url, fileName = fileName, title = title)
        }

        fun enqueueDirect(context: Context, taskId: String) {
            startForegroundAction(context, ACTION_ENQUEUE_DIRECT, taskId)
        }

        fun enqueueVault(context: Context, taskId: String) {
            startForegroundAction(context, ACTION_ENQUEUE_VAULT, taskId)
        }

        fun resume(context: Context, taskId: String) {
            DownloadStore.update(context, taskId, persist = true) { item ->
                item.copy(
                    statusName = if (item.url.isBlank() && item.vaultPayloadJson != null) {
                        DownloadStatus.RESOLVING.name
                    } else {
                        DownloadStatus.QUEUED.name
                    },
                    errorMessage = null,
                    speedBytesPerSec = 0L,
                    etaSeconds = -1L,
                )
            }
            val item = DownloadStore.getById(context, taskId)
            val action = if (item != null && item.url.isBlank() && item.vaultPayloadJson != null) {
                ACTION_ENQUEUE_VAULT
            } else {
                ACTION_RESUME
            }
            startForegroundAction(context, action, taskId)
        }

        fun retry(context: Context, taskId: String) {
            val item = DownloadStore.getById(context, taskId) ?: return
            val hasPayload = item.vaultPayloadJson != null
            DownloadStore.update(context, taskId, persist = true) { current ->
                current.copy(
                    statusName = if (current.url.isBlank() && hasPayload) DownloadStatus.RESOLVING.name else DownloadStatus.QUEUED.name,
                    errorMessage = null,
                    cloudFallbackUrl = null,
                    speedBytesPerSec = 0L,
                    etaSeconds = -1L,
                )
            }
            val action = if (item.url.isBlank() && hasPayload) ACTION_ENQUEUE_VAULT else ACTION_RETRY
            startForegroundAction(context, action, taskId)
        }

        fun pause(context: Context, taskId: String) {
            DownloadStore.update(context, taskId, persist = true) { item ->
                if (item.statusEnum.isActive) {
                    item.copy(
                        statusName = DownloadStatus.PAUSED.name,
                        speedBytesPerSec = 0L,
                        etaSeconds = -1L,
                        errorMessage = null,
                    )
                } else {
                    item
                }
            }
            sendBackgroundActionIfPossible(context, ACTION_PAUSE, taskId)
        }

        fun cancelAndRemove(context: Context, taskId: String) {
            val item = DownloadStore.getById(context, taskId)
            if (item?.partFilePath != null) {
                runCatching { File(item.partFilePath).delete() }
            }
            runCatching { File(getPartsDir(context), "$taskId.part").delete() }
            DownloadStore.removeById(context, taskId)
            sendBackgroundActionIfPossible(context, ACTION_CANCEL, taskId)
        }

        fun pauseAll(context: Context) {
            DownloadStore.getAll(context)
                .filter { it.isEngineManaged && it.statusEnum.isActive }
                .forEach { item ->
                    DownloadStore.update(context, item.id, persist = false) {
                        it.copy(
                            statusName = DownloadStatus.PAUSED.name,
                            speedBytesPerSec = 0L,
                            etaSeconds = -1L,
                            errorMessage = null,
                        )
                    }
                }
            DownloadStore.persistNow(context)
            sendBackgroundActionIfPossible(context, ACTION_PAUSE_ALL, null)
        }

        fun resumeAll(context: Context) {
            val paused = DownloadStore.getAll(context)
                .filter {
                    it.isEngineManaged &&
                        (it.statusEnum == DownloadStatus.PAUSED || it.statusEnum == DownloadStatus.FAILED)
                }
            paused.forEach { resume(context, it.id) }
        }

        private fun startForegroundAction(context: Context, action: String, taskId: String) {
            val appCtx = context.applicationContext
            val intent = Intent(appCtx, DownloadService::class.java).apply {
                this.action = action
                putExtra(EXTRA_TASK_ID, taskId)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appCtx.startForegroundService(intent)
                } else {
                    appCtx.startService(intent)
                }
            } catch (_: Exception) {
                try {
                    appCtx.startService(intent)
                } catch (_: Exception) {}
            }
        }

        private fun sendBackgroundActionIfPossible(context: Context, action: String, taskId: String?) {
            val appCtx = context.applicationContext
            val intent = Intent(appCtx, DownloadService::class.java).apply {
                this.action = action
                if (taskId != null) putExtra(EXTRA_TASK_ID, taskId)
            }
            try {
                appCtx.startService(intent)
            } catch (_: Exception) {
                // Service wasn't running (e.g. cold restart), store update already applied.
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        DownloadStore.ensureInitialized(this)
        ensureChannels()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keep Foreground Service alive when user swipes NexiPlay away from Recents!
        super.onTaskRemoved(rootIntent)
        if (activeJobs.isNotEmpty()) {
            refreshSummaryNotification(force = true)
        }
    }

    override fun onDestroy() {
        releaseLocks()
        activeCalls.values.forEach { runCatching { it.cancel() } }
        activeCalls.clear()
        scope.cancel()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val taskId = intent?.getStringExtra(EXTRA_TASK_ID)

        // If started as foreground or handling any active action, guarantee startForeground immediately on API 26+
        val isForegroundStartAction = action == ACTION_ENQUEUE_DIRECT ||
            action == ACTION_ENQUEUE_VAULT ||
            action == ACTION_RESUME ||
            action == ACTION_RETRY ||
            action == ACTION_RESUME_ALL ||
            action == null

        if (isForegroundStartAction) {
            promoteToForeground(buildSummaryNotification())
        }

        when (action) {
            ACTION_ENQUEUE_DIRECT, ACTION_RESUME, ACTION_RETRY -> {
                if (!taskId.isNullOrBlank()) {
                    launchDownloadTask(taskId, resolveVaultFirst = false)
                }
            }

            ACTION_ENQUEUE_VAULT -> {
                if (!taskId.isNullOrBlank()) {
                    launchDownloadTask(taskId, resolveVaultFirst = true)
                }
            }

            ACTION_PAUSE -> {
                if (!taskId.isNullOrBlank()) {
                    handlePause(taskId)
                }
            }

            ACTION_CANCEL -> {
                if (!taskId.isNullOrBlank()) {
                    handleCancel(taskId)
                }
            }

            ACTION_PAUSE_ALL -> {
                handlePauseAll()
            }

            ACTION_RESUME_ALL -> {
                DownloadStore.getAll(this)
                    .filter { it.isEngineManaged && it.statusEnum == DownloadStatus.PAUSED }
                    .forEach { item ->
                        val resolveFirst = item.url.isBlank() && item.vaultPayloadJson != null
                        launchDownloadTask(item.id, resolveVaultFirst = resolveFirst)
                    }
            }

            null -> {
                // OS restarted START_STICKY service after crash/low-memory kill: resume any paused-with-part tasks or stop cleanly
                checkAndStopIfIdle()
            }
        }

        return if (activeJobs.isNotEmpty()) START_STICKY else START_NOT_STICKY
    }

    private fun launchDownloadTask(taskId: String, resolveVaultFirst: Boolean) {
        if (activeJobs[taskId]?.isActive == true) return
        pauseRequested.remove(taskId)
        cancelRequested.remove(taskId)
        acquireLocks()

        val job = scope.launch {
            try {
                if (resolveVaultFirst) {
                    val resolved = resolveVaultItem(taskId)
                    if (!resolved) return@launch
                }

                val current = DownloadStore.getById(this@DownloadService, taskId) ?: return@launch
                if (current.url.isBlank()) {
                    failTask(taskId, "Missing download URL")
                    return@launch
                }

                DownloadStore.update(this@DownloadService, taskId, persist = true) {
                    if (it.statusEnum != DownloadStatus.DOWNLOADING) {
                        it.copy(statusName = DownloadStatus.QUEUED.name, errorMessage = null)
                    } else {
                        it
                    }
                }
                refreshSummaryNotification(force = true)

                downloadSemaphore.withPermit {
                    if (!isActive || pauseRequested.contains(taskId) || cancelRequested.contains(taskId)) {
                        return@withPermit
                    }
                    performHttpDownload(taskId)
                }
            } catch (_: CancellationException) {
                // Handled by handlePause / handleCancel
            } catch (e: Exception) {
                if (!pauseRequested.contains(taskId) && !cancelRequested.contains(taskId)) {
                    failTask(taskId, friendlyErrorMessage(e))
                }
            } finally {
                activeCalls.remove(taskId)?.let { runCatching { it.cancel() } }
                activeJobs.remove(taskId)
                checkAndStopIfIdle()
            }
        }
        activeJobs[taskId] = job
        refreshSummaryNotification(force = true)
    }

    private suspend fun resolveVaultItem(taskId: String): Boolean {
        val item = DownloadStore.getById(this, taskId) ?: return false
        val payloadStr = item.vaultPayloadJson
        if (payloadStr.isNullOrBlank()) {
            return item.url.isNotBlank()
        }

        val payload = runCatching {
            DownloadStore.json.decodeFromString<VaultResolvePayload>(payloadStr)
        }.getOrNull()

        if (payload == null) {
            failTask(taskId, "Invalid download metadata")
            return false
        }

        DownloadStore.update(this, taskId, persist = true) {
            it.copy(
                statusName = DownloadStatus.RESOLVING.name,
                errorMessage = null,
                speedBytesPerSec = 0L,
                etaSeconds = -1L,
            )
        }
        refreshSummaryNotification(force = true)

        val res = try {
            WebFeaturesRepository.resolveVaultDownload(
                isTv = payload.isTv,
                targetId = payload.targetId,
                season = payload.season,
                episode = payload.episode,
                quality = payload.quality,
                title = payload.rawTitle,
                mediaType = payload.mediaType,
                fallbackStreamUrls = payload.fallbackUrls,
            )
        } catch (e: Exception) {
            failTask(taskId, friendlyErrorMessage(e, "Failed to extract download link"))
            return false
        }

        if (pauseRequested.contains(taskId) || cancelRequested.contains(taskId)) return false

        val directTarget = res.directUrl?.takeIf { it.isNotBlank() }
        val proxyOrDirectTarget = res.url?.takeIf { it.isNotBlank() } ?: directTarget

        if (!res.success || proxyOrDirectTarget.isNullOrBlank()) {
            failTask(taskId, res.error ?: "Direct download link unavailable right now.")
            return false
        }

        val resolvedQuality = res.quality ?: payload.quality
        val isCloudMirror = res.source.equals("toonplay", ignoreCase = true) ||
            directTarget?.let { u ->
                val l = u.lowercase()
                l.contains("hubcloud.") || l.contains("gdflix.") || l.contains("drive.google.") ||
                    l.contains("mega.") || l.contains("mediafire.") || l.contains("terabox.") ||
                    l.contains("1024terabox.") || l.contains("pcloud.") || l.contains("fpgo.") ||
                    l.contains("adhlinks.") || l.contains("animesalt.") || l.contains("razorshell.")
            } == true

        if (isCloudMirror) {
            val cloudUrl = res.url ?: directTarget ?: proxyOrDirectTarget
            DownloadStore.update(this, taskId, persist = true) {
                it.copy(
                    statusName = DownloadStatus.FAILED.name,
                    url = cloudUrl,
                    cloudFallbackUrl = cloudUrl,
                    errorMessage = "Cloud mirror host ($resolvedQuality) — tap Open Mirror to download",
                    speedBytesPerSec = 0L,
                    etaSeconds = -1L,
                )
            }
            postCompletionNotification(
                taskId = taskId,
                title = item.title,
                message = "Cloud mirror ready ($resolvedQuality) — tap to open",
                isSuccess = false,
            )
            return false
        }

        val epSuffix = if (payload.season != null && payload.episode != null) "S${payload.season}E${payload.episode}_" else ""
        val fallbackName = "${payload.rawTitle.replace(Regex("[^a-zA-Z0-9._-]"), "_")}_${epSuffix}${resolvedQuality}.${
            if (proxyOrDirectTarget.contains(".mp4", true) && !proxyOrDirectTarget.contains(".mkv", true)) "mp4" else "mkv"
        }"
        val safeFileName = DownloadRepository.sanitizeFileName(res.fileName?.takeIf { it.isNotBlank() } ?: fallbackName, proxyOrDirectTarget)
        val mime = DownloadRepository.guessMimeType(safeFileName, proxyOrDirectTarget)
        val headers = mapOf(
            "Referer" to inferReferer(proxyOrDirectTarget),
            "User-Agent" to DEFAULT_UA,
        )

        com.nexiplay.app.data.util.CoinRewardHelper.recordDownload(this)

        DownloadStore.update(this, taskId, persist = true) {
            it.copy(
                title = "${payload.rawTitle}${if (payload.season != null && payload.episode != null) " S${payload.season}E${payload.episode}" else ""} ($resolvedQuality)",
                url = proxyOrDirectTarget,
                fileName = safeFileName,
                mimeType = mime,
                headersJson = DownloadStore.json.encodeToString(headers),
                statusName = DownloadStatus.QUEUED.name,
                errorMessage = null,
            )
        }
        return true
    }

    private suspend fun performHttpDownload(taskId: String) {
        var item = DownloadStore.getById(this, taskId) ?: return
        var activeUrl = item.url
        var headers = parseHeaders(item.headersJson)
        val partFile = File(getPartsDir(this), "${item.id}.part")

        DownloadStore.update(this, taskId, persist = true) {
            it.copy(
                statusName = DownloadStatus.DOWNLOADING.name,
                partFilePath = partFile.absolutePath,
                errorMessage = null,
            )
        }
        refreshSummaryNotification(force = true)

        var retryAttempt = 0
        var reResolvedOnce = false
        val maxRetries = 5

        while (coroutineContext.isActive && !pauseRequested.contains(taskId) && !cancelRequested.contains(taskId)) {
            var response: Response? = null
            try {
                val existingBytes = if (partFile.exists()) partFile.length() else 0L
                response = executeWithRedirects(taskId, activeUrl, headers, existingBytes)

                val code = response.code
                if (code == 416) {
                    // Range Not Satisfiable: check if file is already completely downloaded
                    val contentRange = response.header("Content-Range").orEmpty()
                    val serverTotal = contentRange.substringAfter("/", "").toLongOrNull() ?: -1L
                    response.close()
                    if (serverTotal > 0L && existingBytes >= serverTotal) {
                        finalizeDownload(taskId, partFile)
                        return
                    } else {
                        partFile.delete()
                        retryAttempt++
                        continue
                    }
                }

                if (code == 401 || code == 403 || code == 410) {
                    response.close()
                    if (!reResolvedOnce && item.vaultPayloadJson != null) {
                        reResolvedOnce = true
                        if (resolveVaultItem(taskId)) {
                            item = DownloadStore.getById(this, taskId) ?: return
                            activeUrl = item.url
                            headers = parseHeaders(item.headersJson)
                            continue
                        }
                        return
                    }
                    throw IOException("Access denied or link expired (HTTP $code)")
                }

                if (!response.isSuccessful) {
                    val statusCode = response.code
                    response.close()
                    if (statusCode in listOf(429, 500, 502, 503, 504) && retryAttempt < maxRetries) {
                        retryAttempt++
                        waitForRetryAndNetwork(taskId, retryAttempt)
                        continue
                    }
                    throw IOException("Server returned HTTP $statusCode")
                }

                val body = response.body ?: throw IOException("Empty server response")
                val contentType = response.header("Content-Type").orEmpty().lowercase(Locale.ROOT)
                val bodyLength = body.contentLength()

                // Reject accidental HTML / JSON error pages masquerading as video
                if ((contentType.contains("text/html") || contentType.contains("application/json")) &&
                    (bodyLength in 0L..512_000L)
                ) {
                    response.close()
                    if (!reResolvedOnce && item.vaultPayloadJson != null) {
                        reResolvedOnce = true
                        partFile.delete()
                        if (resolveVaultItem(taskId)) {
                            item = DownloadStore.getById(this, taskId) ?: return
                            activeUrl = item.url
                            headers = parseHeaders(item.headersJson)
                            continue
                        }
                        return
                    }
                    throw IOException("Server returned a webpage instead of a video file")
                }

                val isAppend = (code == 206 && existingBytes > 0L)
                var bytesWritten = if (isAppend) existingBytes else 0L
                val totalBytes = when {
                    code == 206 -> {
                        val crTotal = response.header("Content-Range")?.substringAfter("/", "")?.toLongOrNull()
                        crTotal ?: if (bodyLength > 0) existingBytes + bodyLength else -1L
                    }
                    bodyLength > 0 -> bodyLength
                    else -> -1L
                }

                if (totalBytes > 0L) {
                    checkAvailableSpace(partFile.parentFile ?: filesDir, totalBytes - bytesWritten)
                }

                // Resolve better filename/mime from final response URL or Content-Disposition if needed
                val dispName = parseContentDispositionFileName(response.header("Content-Disposition"))
                val currentFileName = if (item.fileName.isBlank() && dispName != null) {
                    DownloadRepository.sanitizeFileName(dispName, activeUrl)
                } else {
                    item.fileName.ifBlank { DownloadRepository.sanitizeFileName("video.mp4", activeUrl) }
                }

                DownloadStore.update(this, taskId, persist = true) {
                    it.copy(
                        fileName = currentFileName,
                        totalBytes = totalBytes,
                        bytesDownloaded = bytesWritten,
                        statusName = DownloadStatus.DOWNLOADING.name,
                    )
                }

                val out = FileOutputStream(partFile, isAppend)
                val inStream = body.byteStream()
                val buffer = ByteArray(64 * 1024)

                var windowBytes = 0L
                var windowStartMs = System.currentTimeMillis()
                var lastUiUpdateMs = windowStartMs
                var lastDiskPersistMs = windowStartMs
                var emaSpeedBps = 0.0

                try {
                    while (coroutineContext.isActive &&
                        !pauseRequested.contains(taskId) &&
                        !cancelRequested.contains(taskId)
                    ) {
                        val read = inStream.read(buffer)
                        if (read == -1) break
                        out.write(buffer, 0, read)
                        bytesWritten += read
                        windowBytes += read

                        val now = System.currentTimeMillis()
                        val elapsedUi = now - lastUiUpdateMs
                        if (elapsedUi >= 450L) {
                            val dtSec = ((now - windowStartMs).coerceAtLeast(1L)) / 1000.0
                            val instantBps = windowBytes / dtSec
                            emaSpeedBps = if (emaSpeedBps <= 0.0) instantBps else (emaSpeedBps * 0.45 + instantBps * 0.55)
                            val speedLong = emaSpeedBps.toLong().coerceAtLeast(0L)
                            val etaSec = if (totalBytes > 0L && speedLong > 0L) {
                                ((totalBytes - bytesWritten).coerceAtLeast(0L) / speedLong)
                            } else {
                                -1L
                            }

                            val shouldPersist = (now - lastDiskPersistMs) >= 3_000L
                            DownloadStore.update(this, taskId, persist = shouldPersist) {
                                it.copy(
                                    bytesDownloaded = bytesWritten,
                                    totalBytes = totalBytes,
                                    speedBytesPerSec = speedLong,
                                    etaSeconds = etaSec,
                                    statusName = DownloadStatus.DOWNLOADING.name,
                                )
                            }
                            if (shouldPersist) lastDiskPersistMs = now
                            lastUiUpdateMs = now
                            windowBytes = 0L
                            windowStartMs = now

                            if (now - lastNotifUpdateMs >= 900L) {
                                refreshSummaryNotification(force = false)
                            }
                        }
                    }
                    out.flush()
                } finally {
                    runCatching { out.close() }
                    runCatching { inStream.close() }
                    runCatching { response.close() }
                }

                if (pauseRequested.contains(taskId) || cancelRequested.contains(taskId) || !coroutineContext.isActive) {
                    return
                }

                // Completed stream read! Verify size if known
                val actualLen = partFile.length()
                if (totalBytes > 0L && actualLen < totalBytes) {
                    // Server closed stream early! Retry using Range from actualLen
                    if (retryAttempt < maxRetries) {
                        retryAttempt++
                        waitForRetryAndNetwork(taskId, retryAttempt)
                        continue
                    } else {
                        throw IOException("Stream closed early (${formatBytes(actualLen)} / ${formatBytes(totalBytes)})")
                    }
                }

                finalizeDownload(taskId, partFile)
                return
            } catch (e: CancellationException) {
                runCatching { response?.close() }
                throw e
            } catch (e: IOException) {
                runCatching { response?.close() }
                if (pauseRequested.contains(taskId) || cancelRequested.contains(taskId)) {
                    return
                }
                if (retryAttempt < maxRetries && isTransientNetworkError(e)) {
                    retryAttempt++
                    waitForRetryAndNetwork(taskId, retryAttempt)
                    continue
                }
                throw e
            }
        }
    }

    private fun executeWithRedirects(
        taskId: String,
        initialUrl: String,
        initialHeaders: Map<String, String>,
        rangeStart: Long,
    ): Response {
        var currentUrl = initialUrl
        var redirects = 0
        val initialHost = runCatching { URI(initialUrl).host.orEmpty() }.getOrDefault("")

        while (redirects < 10) {
            val reqBuilder = Request.Builder()
                .url(currentUrl)
                .header("Accept", "*/*")
                .header("Accept-Encoding", "identity")

            val ua = initialHeaders["User-Agent"] ?: DEFAULT_UA
            reqBuilder.header("User-Agent", ua)

            val explicitReferer = initialHeaders["Referer"]
            val referer = explicitReferer?.takeIf { it.isNotBlank() } ?: inferReferer(currentUrl)
            if (referer.isNotBlank()) {
                reqBuilder.header("Referer", referer)
            }

            val cookie = initialHeaders["Cookie"]
            val currentHost = runCatching { URI(currentUrl).host.orEmpty() }.getOrDefault("")
            if (!cookie.isNullOrBlank() && (currentHost.equals(initialHost, true) || redirects == 0)) {
                reqBuilder.header("Cookie", cookie)
            }

            if (rangeStart > 0L) {
                reqBuilder.header("Range", "bytes=$rangeStart-")
            }

            val call = httpClient.newCall(reqBuilder.build())
            activeCalls[taskId] = call
            val response = call.execute()

            if (response.code in listOf(301, 302, 303, 307, 308)) {
                val location = response.header("Location")
                val resolved = if (!location.isNullOrBlank()) {
                    response.request.url.resolve(location)?.toString()
                } else {
                    null
                }
                response.close()
                if (resolved.isNullOrBlank()) {
                    throw IOException("Invalid redirect target from server")
                }
                currentUrl = resolved
                redirects++
                continue
            }

            return response
        }
        throw IOException("Too many redirects")
    }

    private suspend fun waitForRetryAndNetwork(taskId: String, attempt: Int) {
        DownloadStore.update(this, taskId, persist = false) {
            it.copy(
                speedBytesPerSec = 0L,
                etaSeconds = -1L,
                errorMessage = if (!isNetworkAvailable()) "Waiting for internet connection..." else "Reconnecting (attempt $attempt/5)...",
            )
        }
        refreshSummaryNotification(force = true)

        // Wait up to 24s for network if offline
        var waited = 0
        while (!isNetworkAvailable() && waited < 24 &&
            !pauseRequested.contains(taskId) && !cancelRequested.contains(taskId)
        ) {
            delay(1_000L)
            waited++
        }

        val backoffMs = (attempt * 2_000L).coerceAtMost(10_000L)
        delay(backoffMs)
    }

    private fun finalizeDownload(taskId: String, partFile: File) {
        val item = DownloadStore.getById(this, taskId) ?: return
        val fileName = item.fileName.ifBlank { DownloadRepository.sanitizeFileName("video.mp4", item.url) }
        val mime = item.mimeType.ifBlank { DownloadRepository.guessMimeType(fileName, item.url) }
        val finalSize = partFile.length()

        if (!partFile.exists() || finalSize <= 0L) {
            throw IOException("Downloaded file is empty")
        }

        val savedUri = savePartToFinalDestination(partFile, fileName, mime)
        runCatching { partFile.delete() }

        DownloadStore.update(this, taskId, persist = true) {
            it.copy(
                statusName = DownloadStatus.COMPLETED.name,
                bytesDownloaded = finalSize,
                totalBytes = if (it.totalBytes > finalSize) it.totalBytes else finalSize,
                speedBytesPerSec = 0L,
                etaSeconds = 0L,
                localUri = savedUri,
                partFilePath = null,
                errorMessage = null,
            )
        }

        postCompletionNotification(
            taskId = taskId,
            title = item.title,
            message = "Download complete • ${formatBytes(finalSize)}",
            isSuccess = true,
        )
    }

    private fun savePartToFinalDestination(partFile: File, fileName: String, mimeType: String): String {
        val settings = SettingsRepository(this)
        val sdUriStr = settings.sdCardUri

        // 1. Custom SAF SD Card / chosen folder
        if (!sdUriStr.isNullOrBlank()) {
            val treeUri = Uri.parse(sdUriStr)
            val pickedDir = DocumentFile.fromTreeUri(this, treeUri)
            if (pickedDir != null && pickedDir.canWrite()) {
                val targetDir = pickedDir.findFile("NexiPlay")?.takeIf { it.isDirectory }
                    ?: pickedDir.createDirectory("NexiPlay")
                    ?: pickedDir
                val existing = targetDir.findFile(fileName)
                existing?.delete()
                val docFile = targetDir.createFile(mimeType, fileName)
                    ?: throw IOException("Unable to create file on selected SD Card / folder")
                contentResolver.openOutputStream(docFile.uri)?.use { out ->
                    FileInputStream(partFile).use { input ->
                        input.copyTo(out, bufferSize = 64 * 1024)
                    }
                    out.flush()
                } ?: throw IOException("Unable to write to SD Card")
                return docFile.uri.toString()
            }
        }

        // 2. Scoped Storage MediaStore.Downloads on Android 10+ (API 29+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/NexiPlay")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("Failed to create MediaStore download entry")
            try {
                contentResolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(partFile).use { input ->
                        input.copyTo(out, bufferSize = 64 * 1024)
                    }
                    out.flush()
                } ?: throw IOException("Unable to open download output stream")

                val doneValues = ContentValues().apply {
                    put(MediaStore.Downloads.IS_PENDING, 0)
                }
                contentResolver.update(uri, doneValues, null, null)
                return uri.toString()
            } catch (e: Exception) {
                runCatching { contentResolver.delete(uri, null, null) }
                throw e
            }
        }

        // 3. Legacy public Downloads/NexiPlay folder (Android 7 - 9)
        val downloadsRoot = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val targetDir = File(downloadsRoot, "NexiPlay").apply { if (!exists()) mkdirs() }
        val destFile = uniqueFile(targetDir, fileName)
        FileInputStream(partFile).use { input ->
            FileOutputStream(destFile).use { out ->
                input.copyTo(out, bufferSize = 64 * 1024)
                out.flush()
            }
        }
        MediaScannerConnection.scanFile(this, arrayOf(destFile.absolutePath), arrayOf(mimeType), null)
        return Uri.fromFile(destFile).toString()
    }

    private fun handlePause(taskId: String) {
        pauseRequested.add(taskId)
        activeCalls.remove(taskId)?.let { runCatching { it.cancel() } }
        activeJobs.remove(taskId)?.cancel()

        DownloadStore.update(this, taskId, persist = true) { item ->
            item.copy(
                statusName = DownloadStatus.PAUSED.name,
                speedBytesPerSec = 0L,
                etaSeconds = -1L,
                errorMessage = null,
            )
        }
        checkAndStopIfIdle()
    }

    private fun handleCancel(taskId: String) {
        cancelRequested.add(taskId)
        activeCalls.remove(taskId)?.let { runCatching { it.cancel() } }
        activeJobs.remove(taskId)?.cancel()

        val item = DownloadStore.getById(this, taskId)
        if (item?.partFilePath != null) {
            runCatching { File(item.partFilePath).delete() }
        }
        runCatching { File(getPartsDir(this), "$taskId.part").delete() }
        DownloadStore.removeById(this, taskId)

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(20000 + (taskId.hashCode() and 0x7FFF))
        checkAndStopIfIdle()
    }

    private fun handlePauseAll() {
        val ids = activeJobs.keys.toList()
        ids.forEach { id ->
            pauseRequested.add(id)
            activeCalls.remove(id)?.let { runCatching { it.cancel() } }
            activeJobs.remove(id)?.cancel()
            DownloadStore.update(this, id, persist = false) {
                it.copy(
                    statusName = DownloadStatus.PAUSED.name,
                    speedBytesPerSec = 0L,
                    etaSeconds = -1L,
                    errorMessage = null,
                )
            }
        }
        DownloadStore.persistNow(this)
        checkAndStopIfIdle()
    }

    private fun failTask(taskId: String, message: String) {
        val item = DownloadStore.update(this, taskId, persist = true) {
            it.copy(
                statusName = DownloadStatus.FAILED.name,
                speedBytesPerSec = 0L,
                etaSeconds = -1L,
                errorMessage = message,
            )
        }
        if (item != null) {
            postCompletionNotification(
                taskId = taskId,
                title = item.title,
                message = message,
                isSuccess = false,
            )
        }
    }

    private fun checkAndStopIfIdle() {
        val anyRemaining = activeJobs.isNotEmpty()
        if (!anyRemaining) {
            releaseLocks()
            stopForegroundCompat()
            stopSelf()
        } else {
            refreshSummaryNotification(force = true)
        }
    }

    private fun ensureChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val activeChannel = NotificationChannel(
                CHANNEL_ACTIVE,
                "Active Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Live progress for background downloads"
                setShowBadge(false)
            }
            val alertChannel = NotificationChannel(
                CHANNEL_ALERTS,
                "Download Status Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications when downloads complete or fail"
            }
            nm.createNotificationChannel(activeChannel)
            nm.createNotificationChannel(alertChannel)
        }
    }

    private fun promoteToForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                FOREGROUND_NOTIF_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(FOREGROUND_NOTIF_ID, notification)
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun refreshSummaryNotification(force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - lastNotifUpdateMs < 850L) return
        lastNotifUpdateMs = now
        if (activeJobs.isEmpty()) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(FOREGROUND_NOTIF_ID, buildSummaryNotification())
    }

    private fun buildSummaryNotification(): Notification {
        val allActive = DownloadStore.getAll(this).filter { it.isEngineManaged && it.statusEnum.isActive }
        val openAppIntent = PendingIntent.getActivity(
            this,
            9001,
            Intent(this, MainActivity::class.java).apply {
                putExtra("screen", "downloads")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ACTIVE)
            .setSmallIcon(R.drawable.ic_stat_onesignal_default)
            .apply { if (appIconBitmap != null) setLargeIcon(appIconBitmap) }
            .setColor(0xFFDC2626.toInt())
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(openAppIntent)

        if (allActive.size == 1) {
            val single = allActive.first()
            val status = single.statusEnum
            val pct = if (single.totalBytes > 0L) {
                ((single.bytesDownloaded * 100) / single.totalBytes).toInt().coerceIn(0, 100)
            } else {
                0
            }
            val speedStr = if (single.speedBytesPerSec > 0) "${formatBytes(single.speedBytesPerSec)}/s" else null
            val etaStr = formatEta(single.etaSeconds)
            val detail = when (status) {
                DownloadStatus.RESOLVING -> "Preparing download link..."
                DownloadStatus.QUEUED -> "Queued..."
                else -> buildList {
                    if (single.totalBytes > 0L) {
                        add("$pct% (${formatBytes(single.bytesDownloaded)} / ${formatBytes(single.totalBytes)})")
                    } else if (single.bytesDownloaded > 0L) {
                        add(formatBytes(single.bytesDownloaded))
                    } else {
                        add("Connecting...")
                    }
                    if (speedStr != null) add(speedStr)
                    if (etaStr != null) add(etaStr)
                }.joinToString(" • ")
            }

            builder.setContentTitle(single.title)
                .setContentText(detail)
                .setProgress(100, pct, status != DownloadStatus.DOWNLOADING || single.totalBytes <= 0L)
                .addAction(
                    android.R.drawable.ic_media_pause,
                    "Pause",
                    serviceActionPendingIntent(ACTION_PAUSE, single.id, single.id.hashCode())
                )
                .addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "Cancel",
                    serviceActionPendingIntent(ACTION_CANCEL, single.id, single.id.hashCode() + 1)
                )
        } else if (allActive.size > 1) {
            val totalSpeed = allActive.sumOf { it.speedBytesPerSec }
            val knownTotal = allActive.filter { it.totalBytes > 0L }.sumOf { it.totalBytes }
            val knownDown = allActive.filter { it.totalBytes > 0L }.sumOf { it.bytesDownloaded }
            val aggPct = if (knownTotal > 0L) ((knownDown * 100) / knownTotal).toInt().coerceIn(0, 100) else 0
            val subtitle = buildString {
                if (totalSpeed > 0L) append("${formatBytes(totalSpeed)}/s • ")
                append("$aggPct% overall")
            }

            val inbox = NotificationCompat.InboxStyle()
                .setBigContentTitle("Downloading ${allActive.size} files")
            allActive.take(4).forEach { item ->
                val p = if (item.totalBytes > 0L) "${((item.bytesDownloaded * 100) / item.totalBytes)}%" else formatBytes(item.bytesDownloaded)
                inbox.addLine("${item.title} ($p)")
            }

            builder.setContentTitle("Downloading ${allActive.size} videos")
                .setContentText(subtitle)
                .setStyle(inbox)
                .setProgress(100, aggPct, knownTotal <= 0L)
                .addAction(
                    android.R.drawable.ic_media_pause,
                    "Pause All",
                    serviceActionPendingIntent(ACTION_PAUSE_ALL, null, 9002)
                )
        } else {
            builder.setContentTitle("NexiPlay Download Manager")
                .setContentText("Preparing downloads...")
                .setProgress(100, 0, true)
        }

        return builder.build()
    }

    private fun postCompletionNotification(taskId: String, title: String, message: String, isSuccess: Boolean) {
        val openDownloadsIntent = PendingIntent.getActivity(
            this,
            taskId.hashCode(),
            Intent(this, MainActivity::class.java).apply {
                putExtra("screen", "downloads")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notifId = 20000 + (taskId.hashCode() and 0x7FFF)
        val builder = NotificationCompat.Builder(this, CHANNEL_ALERTS)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setSmallIcon(R.drawable.ic_stat_onesignal_default)
            .apply { if (appIconBitmap != null) setLargeIcon(appIconBitmap) }
            .setColor(if (isSuccess) 0xFF10B981.toInt() else 0xFFEF4444.toInt())
            .setAutoCancel(true)
            .setContentIntent(openDownloadsIntent)

        if (!isSuccess) {
            builder.addAction(
                android.R.drawable.ic_popup_sync,
                "Retry",
                PendingIntent.getService(
                    this,
                    notifId + 1,
                    Intent(this, DownloadService::class.java).apply {
                        action = ACTION_RETRY
                        putExtra(EXTRA_TASK_ID, taskId)
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
        }

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(notifId, builder.build())
    }

    private fun serviceActionPendingIntent(action: String, taskId: String?, requestCode: Int): PendingIntent {
        val intent = Intent(this, DownloadService::class.java).apply {
            this.action = action
            if (taskId != null) putExtra(EXTRA_TASK_ID, taskId)
        }
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun acquireLocks() {
        synchronized(lockMutex) {
            try {
                if (wakeLock == null) {
                    val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
                    wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NexiPlay::DownloadWakeLock")?.apply {
                        setReferenceCounted(false)
                    }
                }
                if (wakeLock?.isHeld == false) {
                    wakeLock?.acquire(6 * 60 * 60 * 1000L) // Up to 6h safety ceiling
                }
            } catch (_: Exception) {}

            try {
                if (wifiLock == null) {
                    val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                    @Suppress("DEPRECATION")
                    val mode = WifiManager.WIFI_MODE_FULL_HIGH_PERF
                    wifiLock = wm?.createWifiLock(mode, "NexiPlay::DownloadWifiLock")?.apply {
                        setReferenceCounted(false)
                    }
                }
                if (wifiLock?.isHeld == false) {
                    wifiLock?.acquire()
                }
            } catch (_: Exception) {}
        }
    }

    private fun releaseLocks() {
        synchronized(lockMutex) {
            try {
                if (wakeLock?.isHeld == true) wakeLock?.release()
            } catch (_: Exception) {}
            try {
                if (wifiLock?.isHeld == true) wifiLock?.release()
            } catch (_: Exception) {}
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun isTransientNetworkError(e: IOException): Boolean {
        val msg = (e.message ?: "").lowercase(Locale.ROOT)
        return e is java.net.SocketTimeoutException ||
            e is java.io.EOFException ||
            e is java.net.ConnectException ||
            e is java.net.UnknownHostException ||
            e is javax.net.ssl.SSLException ||
            msg.contains("reset") ||
            msg.contains("broken pipe") ||
            msg.contains("unexpected end") ||
            msg.contains("timeout")
    }

    private fun checkAvailableSpace(dir: File, bytesNeeded: Long) {
        if (bytesNeeded <= 0L) return
        try {
            val stat = StatFs(dir.absolutePath)
            val avail = stat.availableBytes
            if (avail < bytesNeeded + 5 * 1024 * 1024L) {
                throw IOException("Not enough storage space (${formatBytes(avail)} free)")
            }
        } catch (e: IOException) {
            throw e
        } catch (_: Exception) {}
    }

    private fun parseHeaders(jsonStr: String): Map<String, String> {
        if (jsonStr.isBlank()) return emptyMap()
        return runCatching {
            DownloadStore.json.decodeFromString<Map<String, String>>(jsonStr)
        }.getOrDefault(emptyMap())
    }

    private fun inferReferer(url: String): String {
        val lower = url.lowercase(Locale.ROOT)
        return when {
            lower.contains("animeheaven.") -> "https://animeheaven.me/"
            lower.contains("hakunaymatata.") -> "https://netfilm.world/"
            lower.contains("vidvault.") || lower.contains("workers.dev") -> "https://vidvault.to/"
            lower.contains("razorshell.space") -> "https://argon.razorshell.space/"
            else -> ""
        }
    }

    private fun parseContentDispositionFileName(header: String?): String? {
        if (header.isNullOrBlank()) return null
        val match = Regex("""filename\*?=(?:UTF-8'')?["']?([^"';]+)["']?""", RegexOption.IGNORE_CASE).find(header)
        return match?.groupValues?.getOrNull(1)?.trim()
    }

    private fun uniqueFile(dir: File, name: String): File {
        var candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 1
        while (candidate.exists()) {
            candidate = File(dir, "$base ($i)$ext")
            i++
        }
        return candidate
    }

    private fun friendlyErrorMessage(e: Exception, fallback: String = "Download failed"): String {
        val msg = e.message?.trim().orEmpty()
        return when {
            msg.isNotBlank() && msg.length <= 120 -> msg
            e is java.net.UnknownHostException -> "No internet connection"
            e is java.net.SocketTimeoutException -> "Connection timed out — tap Retry"
            else -> fallback
        }
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024L -> "$bytes B"
            bytes < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
            bytes < 1024L * 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
            else -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }

    private fun formatEta(seconds: Long): String? {
        if (seconds <= 0L) return null
        val h = seconds / 3600L
        val m = (seconds % 3600L) / 60L
        val s = seconds % 60L
        return when {
            h > 0L -> "${h}h ${m}m left"
            m > 0L -> "${m}m ${s}s left"
            else -> "${s}s left"
        }
    }
}
