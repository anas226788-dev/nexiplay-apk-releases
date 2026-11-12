package com.nexiplay.app.data.repository

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

enum class DownloadStatus {
    RESOLVING,
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED;

    val isActive: Boolean
        get() = this == RESOLVING || this == QUEUED || this == DOWNLOADING
}

@Serializable
data class VaultResolvePayload(
    val rawTitle: String,
    val quality: String,
    val isTv: Boolean,
    val targetId: String,
    val season: Int? = null,
    val episode: Int? = null,
    val mediaType: String = "movie",
    val fallbackUrls: List<String> = emptyList(),
)

@Serializable
data class DownloadItem(
    val id: String = UUID.randomUUID().toString(),
    val downloadManagerId: Long = -1L,
    val title: String,
    val url: String,
    val timestamp: Long,
    val fileName: String = "",
    val statusName: String = DownloadStatus.QUEUED.name,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = -1L,
    val speedBytesPerSec: Long = 0L,
    val etaSeconds: Long = -1L,
    val localUri: String? = null,
    val partFilePath: String? = null,
    val errorMessage: String? = null,
    val headersJson: String = "{}",
    val isEngineManaged: Boolean = false,
    val cloudFallbackUrl: String? = null,
    val mimeType: String = "video/mp4",
    val vaultPayloadJson: String? = null,
) {
    val statusEnum: DownloadStatus
        get() = runCatching { DownloadStatus.valueOf(statusName) }.getOrDefault(DownloadStatus.QUEUED)
}

data class DownloadProgress(
    val item: DownloadItem,
    val status: Int,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val reason: Int = 0,
    val localUri: String? = null,
    val speedBytesPerSec: Long = 0L,
    val etaSeconds: Long = -1L,
    val errorMessage: String? = null,
    val cloudFallbackUrl: String? = null,
    val engineStatus: DownloadStatus? = null,
) {
    val progressPercent: Float
        get() = if (totalBytes > 0) (bytesDownloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
    val isComplete: Boolean
        get() = engineStatus == DownloadStatus.COMPLETED || status == DownloadManager.STATUS_SUCCESSFUL
    val isFailed: Boolean
        get() = engineStatus == DownloadStatus.FAILED || engineStatus == DownloadStatus.CANCELLED || status == DownloadManager.STATUS_FAILED
    val isRunning: Boolean
        get() = engineStatus == DownloadStatus.DOWNLOADING || engineStatus == DownloadStatus.RESOLVING || status == DownloadManager.STATUS_RUNNING
    val isPending: Boolean
        get() = engineStatus == DownloadStatus.QUEUED || status == DownloadManager.STATUS_PENDING
    val isPaused: Boolean
        get() = engineStatus == DownloadStatus.PAUSED || status == DownloadManager.STATUS_PAUSED
}

internal object DownloadStore {
    private const val PREFS_NAME = "downloads_prefs"
    private const val KEY_DOWNLOADS = "downloads"

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val lock = Any()
    @Volatile
    private var initialized = false

    private val _downloadsFlow = MutableStateFlow<List<DownloadItem>>(emptyList())
    val downloadsFlow: StateFlow<List<DownloadItem>> = _downloadsFlow.asStateFlow()

    fun ensureInitialized(context: Context) {
        if (initialized) return
        synchronized(lock) {
            if (initialized) return
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val jsonStr = prefs.getString(KEY_DOWNLOADS, null)
            val loaded = if (!jsonStr.isNullOrBlank()) {
                runCatching { json.decodeFromString<List<DownloadItem>>(jsonStr) }.getOrDefault(emptyList())
            } else {
                emptyList()
            }

            // Reconcile any engine-managed tasks that were mid-flight if the OS terminated the process
            var changed = false
            val reconciled = loaded.map { item ->
                if (item.isEngineManaged && item.statusEnum.isActive) {
                    changed = true
                    val hasPartial = !item.partFilePath.isNullOrBlank() && runCatching {
                        val f = File(item.partFilePath)
                        f.exists() && f.length() > 0L
                    }.getOrDefault(false)
                    item.copy(
                        statusName = if (hasPartial) DownloadStatus.PAUSED.name else DownloadStatus.FAILED.name,
                        speedBytesPerSec = 0L,
                        etaSeconds = -1L,
                        errorMessage = if (hasPartial) {
                            "Paused when system stopped background process"
                        } else {
                            "Interrupted before download started — tap Retry"
                        }
                    )
                } else {
                    item
                }
            }

            _downloadsFlow.value = reconciled
            if (changed) {
                prefs.edit().putString(KEY_DOWNLOADS, json.encodeToString(reconciled)).apply()
            }
            initialized = true
        }
    }

    fun getAll(context: Context): List<DownloadItem> {
        ensureInitialized(context)
        return _downloadsFlow.value
    }

    fun getById(context: Context, id: String): DownloadItem? {
        ensureInitialized(context)
        return _downloadsFlow.value.firstOrNull { it.id == id }
    }

    fun upsert(context: Context, item: DownloadItem, persist: Boolean = true) {
        ensureInitialized(context)
        synchronized(lock) {
            val current = _downloadsFlow.value.toMutableList()
            val idx = current.indexOfFirst { it.id == item.id }
            if (idx >= 0) {
                current[idx] = item
            } else {
                current.add(0, item)
            }
            _downloadsFlow.value = current
            if (persist) {
                persistLocked(context, current)
            }
        }
    }

    fun update(
        context: Context,
        id: String,
        persist: Boolean = true,
        transform: (DownloadItem) -> DownloadItem
    ): DownloadItem? {
        ensureInitialized(context)
        synchronized(lock) {
            val current = _downloadsFlow.value.toMutableList()
            val idx = current.indexOfFirst { it.id == id }
            if (idx < 0) return null
            val updated = transform(current[idx])
            current[idx] = updated
            _downloadsFlow.value = current
            if (persist) {
                persistLocked(context, current)
            }
            return updated
        }
    }

    fun persistNow(context: Context) {
        ensureInitialized(context)
        synchronized(lock) {
            persistLocked(context, _downloadsFlow.value)
        }
    }

    fun removeById(context: Context, id: String) {
        ensureInitialized(context)
        synchronized(lock) {
            val current = _downloadsFlow.value.filterNot { it.id == id }
            _downloadsFlow.value = current
            persistLocked(context, current)
        }
    }

    fun removeAll(context: Context, predicate: (DownloadItem) -> Boolean) {
        ensureInitialized(context)
        synchronized(lock) {
            val current = _downloadsFlow.value.filterNot(predicate)
            _downloadsFlow.value = current
            persistLocked(context, current)
        }
    }

    private fun persistLocked(context: Context, list: List<DownloadItem>) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_DOWNLOADS, json.encodeToString(list)).apply()
    }
}

class DownloadRepository(private val context: Context) {
    private val appContext = context.applicationContext
    private val downloadManager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    init {
        DownloadStore.ensureInitialized(appContext)
    }

    val downloadsFlow: StateFlow<List<DownloadItem>>
        get() = DownloadStore.downloadsFlow

    /**
     * Enqueues a background-resilient direct download managed by [DownloadService] ForegroundService.
     */
    fun startDownload(
        url: String,
        title: String,
        fileName: String,
        headers: Map<String, String> = emptyMap(),
        mimeType: String = guessMimeType(fileName, url),
    ): Long {
        enqueueDirectDownload(url = url, fileName = fileName, title = title, headers = headers, mimeType = mimeType)
        return -1L
    }

    fun enqueueDirectDownload(
        url: String,
        fileName: String,
        title: String,
        headers: Map<String, String> = emptyMap(),
        mimeType: String = guessMimeType(fileName, url),
    ): String {
        val cleanName = sanitizeFileName(fileName, url)
        val taskId = UUID.randomUUID().toString()
        val item = DownloadItem(
            id = taskId,
            downloadManagerId = -1L,
            title = title.ifBlank { cleanName },
            url = url,
            timestamp = System.currentTimeMillis(),
            fileName = cleanName,
            statusName = DownloadStatus.QUEUED.name,
            headersJson = DownloadStore.json.encodeToString(headers),
            isEngineManaged = true,
            mimeType = mimeType,
        )
        DownloadStore.upsert(appContext, item, persist = true)
        com.nexiplay.app.data.util.CoinRewardHelper.recordDownload(appContext)
        DownloadService.enqueueDirect(appContext, taskId)
        return taskId
    }

    fun enqueueVaultDownload(
        displayTitle: String,
        rawTitle: String,
        quality: String,
        isTv: Boolean,
        targetId: String,
        season: Int?,
        episode: Int?,
        mediaType: String,
        fallbackUrls: List<String>,
    ): String {
        val payload = VaultResolvePayload(
            rawTitle = rawTitle,
            quality = quality,
            isTv = isTv,
            targetId = targetId,
            season = season,
            episode = episode,
            mediaType = mediaType,
            fallbackUrls = fallbackUrls,
        )
        val taskId = UUID.randomUUID().toString()
        val item = DownloadItem(
            id = taskId,
            downloadManagerId = -1L,
            title = displayTitle,
            url = "",
            timestamp = System.currentTimeMillis(),
            fileName = "",
            statusName = DownloadStatus.RESOLVING.name,
            isEngineManaged = true,
            vaultPayloadJson = DownloadStore.json.encodeToString(payload),
        )
        DownloadStore.upsert(appContext, item, persist = true)
        DownloadService.enqueueVault(appContext, taskId)
        return taskId
    }

    /**
     * Save a legacy system DownloadManager record to SharedPreferences for history tracking.
     */
    fun saveDownloadRecord(downloadManagerId: Long, title: String, url: String) {
        val item = DownloadItem(
            id = UUID.randomUUID().toString(),
            downloadManagerId = downloadManagerId,
            title = title,
            url = url,
            timestamp = System.currentTimeMillis(),
            isEngineManaged = false,
        )
        DownloadStore.upsert(appContext, item, persist = true)
        com.nexiplay.app.data.util.CoinRewardHelper.recordDownload(appContext)
    }

    fun getDownloads(): List<DownloadItem> = DownloadStore.getAll(appContext)

    fun getItemById(id: String): DownloadItem? = DownloadStore.getById(appContext, id)

    fun pauseDownload(id: String) {
        DownloadService.pause(appContext, id)
    }

    fun resumeDownload(id: String) {
        DownloadService.resume(appContext, id)
    }

    fun retryDownload(id: String) {
        DownloadService.retry(appContext, id)
    }

    fun pauseAll() {
        DownloadService.pauseAll(appContext)
    }

    fun resumeAll() {
        DownloadService.resumeAll(appContext)
    }

    fun clearFinished() {
        val finished = getDownloads().filter { item ->
            if (item.isEngineManaged) {
                val st = item.statusEnum
                st == DownloadStatus.COMPLETED || st == DownloadStatus.FAILED || st == DownloadStatus.CANCELLED
            } else {
                val p = getDownloadProgress(item.downloadManagerId)
                p == null || p.isComplete || p.isFailed
            }
        }
        finished.forEach { removeDownload(it) }
    }

    fun toProgress(item: DownloadItem): DownloadProgress? {
        if (item.isEngineManaged) {
            val statusEnum = item.statusEnum
            val dmStatus = when (statusEnum) {
                DownloadStatus.RESOLVING, DownloadStatus.DOWNLOADING -> DownloadManager.STATUS_RUNNING
                DownloadStatus.QUEUED -> DownloadManager.STATUS_PENDING
                DownloadStatus.PAUSED -> DownloadManager.STATUS_PAUSED
                DownloadStatus.COMPLETED -> DownloadManager.STATUS_SUCCESSFUL
                DownloadStatus.FAILED, DownloadStatus.CANCELLED -> DownloadManager.STATUS_FAILED
            }
            return DownloadProgress(
                item = item,
                status = dmStatus,
                bytesDownloaded = item.bytesDownloaded,
                totalBytes = item.totalBytes,
                localUri = item.localUri,
                speedBytesPerSec = item.speedBytesPerSec,
                etaSeconds = item.etaSeconds,
                errorMessage = item.errorMessage,
                cloudFallbackUrl = item.cloudFallbackUrl,
                engineStatus = statusEnum,
            )
        }
        return getDownloadProgress(item.downloadManagerId)?.copy(item = item)
    }

    fun getDownloadProgress(downloadManagerId: Long): DownloadProgress? {
        if (downloadManagerId <= 0L) return null
        val query = DownloadManager.Query().setFilterById(downloadManagerId)
        var cursor: Cursor? = null
        return try {
            cursor = downloadManager.query(query)
            if (cursor != null && cursor.moveToFirst()) {
                val bytesIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val totalIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val reasonIdx = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)

                val bytesDownloaded = if (bytesIdx >= 0) cursor.getLong(bytesIdx) else 0L
                val totalBytes = if (totalIdx >= 0) cursor.getLong(totalIdx) else -1L
                val status = if (statusIdx >= 0) cursor.getInt(statusIdx) else DownloadManager.STATUS_PENDING
                val reason = if (reasonIdx >= 0) cursor.getInt(reasonIdx) else 0

                val localUriIdx = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                val localUriStr = if (localUriIdx >= 0) cursor.getString(localUriIdx) else null

                DownloadProgress(
                    item = DownloadItem(downloadManagerId = downloadManagerId, title = "", url = "", timestamp = 0),
                    status = status,
                    bytesDownloaded = bytesDownloaded,
                    totalBytes = totalBytes,
                    reason = reason,
                    localUri = localUriStr,
                )
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            cursor?.close()
        }
    }

    fun removeDownload(item: DownloadItem) {
        if (item.isEngineManaged) {
            DownloadService.cancelAndRemove(appContext, item.id)
        } else {
            if (item.downloadManagerId > 0L) {
                try { downloadManager.remove(item.downloadManagerId) } catch (_: Exception) {}
            }
            DownloadStore.removeById(appContext, item.id)
        }
    }

    companion object {
        fun sanitizeFileName(rawName: String, url: String = ""): String {
            var clean = rawName
                .substringAfterLast('/')
                .substringBefore('?')
                .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                .trim()
            if (clean.isBlank() || clean.endsWith(".bin", ignoreCase = true) || clean == ".mp4" || clean == ".mkv") {
                val ext = when {
                    url.contains(".mkv", ignoreCase = true) -> "mkv"
                    url.contains(".webm", ignoreCase = true) -> "webm"
                    else -> "mp4"
                }
                clean = "NexiPlay_${System.currentTimeMillis()}.$ext"
            }
            if (!clean.contains('.')) {
                val ext = when {
                    url.contains(".mkv", ignoreCase = true) -> "mkv"
                    url.contains(".webm", ignoreCase = true) -> "webm"
                    else -> "mp4"
                }
                clean = "$clean.$ext"
            }
            return clean.take(120)
        }

        fun guessMimeType(fileName: String, url: String = ""): String {
            val lower = (fileName + " " + url).lowercase()
            return when {
                lower.contains(".mkv") -> "video/x-matroska"
                lower.contains(".webm") -> "video/webm"
                lower.contains(".avi") -> "video/x-msvideo"
                lower.contains(".mov") -> "video/quicktime"
                else -> "video/mp4"
            }
        }
    }
}
