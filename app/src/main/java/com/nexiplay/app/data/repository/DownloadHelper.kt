package com.nexiplay.app.data.repository

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class DownloadItem(
    val id: String = UUID.randomUUID().toString(),
    val downloadManagerId: Long,
    val title: String,
    val url: String,
    val timestamp: Long,
)

data class DownloadProgress(
    val item: DownloadItem,
    val status: Int,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val reason: Int = 0,
    val localUri: String? = null
) {
    val progressPercent: Float
        get() = if (totalBytes > 0) (bytesDownloaded.toFloat() / totalBytes.toFloat()) else 0f
    val isComplete: Boolean
        get() = status == DownloadManager.STATUS_SUCCESSFUL
    val isFailed: Boolean
        get() = status == DownloadManager.STATUS_FAILED
    val isRunning: Boolean
        get() = status == DownloadManager.STATUS_RUNNING
    val isPending: Boolean
        get() = status == DownloadManager.STATUS_PENDING
    val isPaused: Boolean
        get() = status == DownloadManager.STATUS_PAUSED
}

class DownloadRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("downloads_prefs", Context.MODE_PRIVATE)
    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    /**
     * Start a download using the system DownloadManager.
     * Used by the old code path (if any). WebViewScreen now calls startSystemDownload + saveDownloadRecord directly.
     */
    fun startDownload(url: String, title: String, fileName: String): Long {
        return try {
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle(title)
                .setDescription("Downloading via NexiPlay")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "NexiPlay/$fileName")
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            val downloadId = downloadManager.enqueue(request)
            saveDownloadRecord(downloadId, title, url)
            downloadId
        } catch (e: Exception) {
            e.printStackTrace()
            -1L
        }
    }

    /**
     * Save a download record to SharedPreferences for history tracking.
     */
    fun saveDownloadRecord(downloadManagerId: Long, title: String, url: String) {
        val item = DownloadItem(
            id = UUID.randomUUID().toString(),
            downloadManagerId = downloadManagerId,
            title = title,
            url = url,
            timestamp = System.currentTimeMillis()
        )
        val items = getDownloads().toMutableList()
        items.add(0, item)
        prefs.edit().putString("downloads", Json.encodeToString(items)).apply()

        // Track download for daily coin reward (20 coins for 2 downloads)
        com.nexiplay.app.data.util.CoinRewardHelper.recordDownload(context)
    }

    fun getDownloads(): List<DownloadItem> {
        val jsonStr = prefs.getString("downloads", null) ?: return emptyList()
        return try {
            Json.decodeFromString(jsonStr)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getDownloadProgress(downloadManagerId: Long): DownloadProgress? {
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
                    localUri = localUriStr
                )
            } else {
                null
            }
        } catch (e: Exception) {
            null
        } finally {
            cursor?.close()
        }
    }

    fun removeDownload(item: DownloadItem) {
        try { downloadManager.remove(item.downloadManagerId) } catch (_: Exception) {}
        val items = getDownloads().toMutableList()
        items.removeAll { it.id == item.id }
        prefs.edit().putString("downloads", Json.encodeToString(items)).apply()
    }
}
