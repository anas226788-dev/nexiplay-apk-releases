package com.nexiplay.app.data.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import com.nexiplay.app.BuildConfig
import com.nexiplay.app.data.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

@Serializable
data class AppUpdateInfo(
    val id: String = "app_update",
    @SerialName("latest_version_code") val latestVersionCode: Int = 1,
    @SerialName("latest_version_name") val latestVersionName: String = "1.0.0",
    @SerialName("apk_url") val apkUrl: String = "",
    @SerialName("release_notes") val releaseNotes: String = "",
    @SerialName("force_update") val forceUpdate: Boolean = false,
    @SerialName("min_version_code") val minVersionCode: Int = 1
)

/**
 * Industry-grade in-app update manager.
 *
 * Handles:
 * - Fetching update info from Supabase (`app_config` table)
 * - Version comparison with proper code-based logic
 * - APK download with progress tracking & redirect handling
 * - Runtime install permission checks (Android 8+)
 * - Error recovery with retry support
 * - Dismissed-version persistence
 */
object AppUpdateManager {

    // ── State Flows ──
    private val _updateInfo = MutableStateFlow<AppUpdateInfo?>(null)
    val updateInfo = _updateInfo.asStateFlow()

    private val _downloadProgress = MutableStateFlow(-1f) // -1 = idle, 0..1 = progress
    val downloadProgress = _downloadProgress.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading = _isDownloading.asStateFlow()

    enum class UpdateState { IDLE, CONNECTING, DOWNLOADING, INSTALLING, ERROR, COMPLETE }
    private val _updateState = MutableStateFlow(UpdateState.IDLE)
    val updateState = _updateState.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private val _downloadedBytes = MutableStateFlow(0L)
    val downloadedBytes = _downloadedBytes.asStateFlow()

    private val _totalBytes = MutableStateFlow(0L)
    val totalBytes = _totalBytes.asStateFlow()

    // ── Constants ──
    private const val PREFS_NAME = "app_update_prefs"
    private const val KEY_DISMISSED_VERSION_CODE = "dismissed_version_code"
    private const val CONNECT_TIMEOUT = 20_000
    private const val READ_TIMEOUT = 60_000
    private const val MAX_REDIRECTS = 5
    private const val BUFFER_SIZE = 8192

    // ── Computed Properties ──

    val isUpdateAvailable: Boolean
        get() {
            val info = _updateInfo.value ?: return false
            return info.latestVersionCode > BuildConfig.VERSION_CODE && info.apkUrl.isNotBlank()
        }

    val isForceUpdate: Boolean
        get() {
            val info = _updateInfo.value ?: return false
            // Force update if explicitly set OR if current version is below minimum
            return info.forceUpdate || BuildConfig.VERSION_CODE < info.minVersionCode
        }

    /**
     * Check if the update dialog should be shown.
     * Force updates always show. Optional updates respect dismissed version.
     */
    fun shouldShowUpdate(context: Context): Boolean {
        val info = _updateInfo.value ?: return false
        if (!isUpdateAvailable) return false
        if (isForceUpdate) return true

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val dismissedVersionCode = prefs.getInt(KEY_DISMISSED_VERSION_CODE, 0)
        return info.latestVersionCode > dismissedVersionCode
    }

    /**
     * Mark the current update version as dismissed.
     */
    fun dismissUpdatePermanently(context: Context) {
        val info = _updateInfo.value ?: return
        if (isForceUpdate) return

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_DISMISSED_VERSION_CODE, info.latestVersionCode).apply()
    }

    // ── Check for Update ──

    /**
     * Fetch latest update info from Supabase.
     * Reads from `app_config` table with id = 'app_update'.
     */
    suspend fun checkForUpdate() {
        withContext(Dispatchers.IO) {
            try {
                val info = SupabaseClient.main.from("app_config")
                    .select {
                        filter { eq("id", "app_update") }
                    }
                    .decodeSingleOrNull<AppUpdateInfo>()

                _updateInfo.value = info
            } catch (e: Exception) {
                e.printStackTrace()
                _updateInfo.value = null
            }
        }
    }

    // ── Download & Install ──

    /**
     * Download APK with full redirect chain handling and install.
     *
     * Handles:
     * - HTTP 301/302/303/307/308 redirects (up to MAX_REDIRECTS)
     * - Google Drive large-file confirmation pages
     * - Progress tracking with byte counts
     * - Error recovery with meaningful messages
     */
    suspend fun downloadAndInstall(context: Context) {
        val info = _updateInfo.value ?: return
        if (info.apkUrl.isBlank()) {
            _errorMessage.value = "No download URL configured"
            _updateState.value = UpdateState.ERROR
            return
        }

        // Check install permission on Android 8+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                // Prompt user to enable install from unknown sources
                withContext(Dispatchers.Main) {
                    try {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:${context.packageName}")
                        ).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                        Toast.makeText(
                            context,
                            "Please allow app installation, then try again",
                            Toast.LENGTH_LONG
                        ).show()
                    } catch (e: Exception) {
                        Toast.makeText(
                            context,
                            "Please enable 'Install unknown apps' in Settings",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                return
            }
        }

        _isDownloading.value = true
        _downloadProgress.value = 0f
        _errorMessage.value = null
        _updateState.value = UpdateState.CONNECTING
        _downloadedBytes.value = 0L
        _totalBytes.value = 0L

        withContext(Dispatchers.IO) {
            try {
                val cacheDir = File(context.cacheDir, "apk_updates")
                if (!cacheDir.exists()) cacheDir.mkdirs()

                // Clean old APKs
                cacheDir.listFiles()?.forEach { it.delete() }

                val apkFile = File(cacheDir, "nexiplay-${info.latestVersionName}.apk")

                // Resolve final download URL (handle redirects + Google Drive confirmation)
                val finalUrl = resolveDownloadUrl(info.apkUrl)

                val url = URL(finalUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = CONNECT_TIMEOUT
                connection.readTimeout = READ_TIMEOUT
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("User-Agent", "NexiPlay/${BuildConfig.VERSION_NAME}")
                connection.connect()

                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    throw Exception("Server returned HTTP $responseCode")
                }

                val contentType = connection.contentType ?: ""
                // Verify we're not downloading an HTML page instead of an APK
                if (contentType.contains("text/html", ignoreCase = true)) {
                    connection.disconnect()
                    throw Exception("Download URL returned HTML instead of APK. Please use a direct download link.")
                }

                val totalSize = connection.contentLength.toLong()
                _totalBytes.value = totalSize
                var downloadedSize = 0L

                _updateState.value = UpdateState.DOWNLOADING

                connection.inputStream.use { input ->
                    apkFile.outputStream().use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedSize += bytesRead
                            _downloadedBytes.value = downloadedSize
                            if (totalSize > 0) {
                                _downloadProgress.value = downloadedSize.toFloat() / totalSize.toFloat()
                            }
                        }
                    }
                }

                connection.disconnect()

                // Verify downloaded file
                if (!apkFile.exists() || apkFile.length() < 1024) {
                    throw Exception("Download incomplete or file corrupted")
                }

                _downloadProgress.value = 1f
                _updateState.value = UpdateState.INSTALLING

                // Trigger install on Main thread
                withContext(Dispatchers.Main) {
                    installApk(context, apkFile)
                    _updateState.value = UpdateState.COMPLETE
                }

            } catch (e: Exception) {
                e.printStackTrace()
                val userMessage = when {
                    e.message?.contains("timeout", ignoreCase = true) == true ->
                        "Connection timed out. Please check your internet and try again."
                    e.message?.contains("HTML", ignoreCase = true) == true ->
                        e.message ?: "Invalid download URL"
                    e.message?.contains("HTTP", ignoreCase = true) == true ->
                        e.message ?: "Server error"
                    e.message?.contains("Unable to resolve host", ignoreCase = true) == true ->
                        "No internet connection. Please check your network."
                    e.message?.contains("corrupted", ignoreCase = true) == true ->
                        "Download was incomplete. Please try again."
                    else ->
                        "Download failed: ${e.localizedMessage ?: "Unknown error"}"
                }
                _errorMessage.value = userMessage
                _downloadProgress.value = -1f
                _updateState.value = UpdateState.ERROR
            } finally {
                _isDownloading.value = false
            }
        }
    }

    /**
     * Resolve the final direct download URL by following redirects manually.
     * Handles Google Drive large-file confirmation pages.
     */
    private fun resolveDownloadUrl(originalUrl: String): String {
        var currentUrl = originalUrl
        var redirectCount = 0

        // Handle Google Drive URLs — convert share links to direct download
        if (currentUrl.contains("drive.google.com") || currentUrl.contains("docs.google.com")) {
            val fileId = extractGDriveFileId(currentUrl)
            if (fileId != null) {
                currentUrl = "https://drive.google.com/uc?export=download&id=$fileId&confirm=t"
            }
        }

        // Follow redirects manually to handle confirmation pages
        while (redirectCount < MAX_REDIRECTS) {
            val url = URL(currentUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = CONNECT_TIMEOUT
            conn.readTimeout = READ_TIMEOUT
            conn.setRequestProperty("User-Agent", "NexiPlay/${BuildConfig.VERSION_NAME}")
            conn.connect()

            val code = conn.responseCode

            if (code in listOf(301, 302, 303, 307, 308)) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location.isNullOrBlank()) break
                currentUrl = if (location.startsWith("http")) location
                else URL(url, location).toString()
                redirectCount++
                continue
            }

            // Google Drive virus scan confirmation — check for download_warning cookie
            if (code == 200 && (currentUrl.contains("drive.google.com") || currentUrl.contains("docs.google.com"))) {
                val contentType = conn.contentType ?: ""
                if (contentType.contains("text/html")) {
                    // Get the confirmation cookie
                    val cookies = conn.headerFields["Set-Cookie"]
                    val confirmCookie = cookies?.find { it.contains("download_warning") }
                    val confirmToken = confirmCookie
                        ?.substringAfter("download_warning=")
                        ?.substringBefore(";")

                    conn.disconnect()

                    if (confirmToken != null) {
                        val fileId = extractGDriveFileId(currentUrl)
                        currentUrl = "https://drive.google.com/uc?export=download&id=$fileId&confirm=$confirmToken"
                        redirectCount++
                        continue
                    }
                    // Fallback: add &confirm=t
                    currentUrl = if (currentUrl.contains("confirm=")) currentUrl
                    else "$currentUrl&confirm=t"
                    redirectCount++
                    continue
                }
            }

            conn.disconnect()
            break
        }

        return currentUrl
    }

    /**
     * Extract Google Drive file ID from various URL formats.
     */
    private fun extractGDriveFileId(url: String): String? {
        // Format: https://drive.google.com/file/d/FILE_ID/view
        val pattern1 = Regex("/file/d/([a-zA-Z0-9_-]+)")
        pattern1.find(url)?.groupValues?.get(1)?.let { return it }

        // Format: https://drive.google.com/uc?export=download&id=FILE_ID
        val pattern2 = Regex("[?&]id=([a-zA-Z0-9_-]+)")
        pattern2.find(url)?.groupValues?.get(1)?.let { return it }

        // Format: https://drive.google.com/open?id=FILE_ID
        val pattern3 = Regex("open\\?id=([a-zA-Z0-9_-]+)")
        pattern3.find(url)?.groupValues?.get(1)?.let { return it }

        return null
    }

    /**
     * Launch Android PackageInstaller for the downloaded APK.
     */
    private fun installApk(context: Context, apkFile: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            _errorMessage.value = "Failed to open installer: ${e.localizedMessage}"
            _updateState.value = UpdateState.ERROR
        }
    }

    // ── State Management ──

    /**
     * Reset all download/error state for retry or dismissal.
     */
    fun resetState() {
        _downloadProgress.value = -1f
        _isDownloading.value = false
        _errorMessage.value = null
        _updateState.value = UpdateState.IDLE
        _downloadedBytes.value = 0L
        _totalBytes.value = 0L
    }

    /**
     * Legacy dismiss — just resets download progress.
     */
    fun dismissUpdate() {
        resetState()
    }

    /**
     * Format bytes to human-readable string (e.g. "12.5 MB").
     */
    fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
            else -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }
}
