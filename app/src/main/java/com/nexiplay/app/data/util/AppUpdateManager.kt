package com.nexiplay.app.data.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.nexiplay.app.BuildConfig
import com.nexiplay.app.data.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File
import java.io.FileInputStream
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
 * In-app OTA manager supporting silent background APK prefetching for minor updates
 * and instant 1-tap installation when ready or when a major/force update is triggered.
 */
object AppUpdateManager {

    // ── State Flows ──
    private val _updateInfo = MutableStateFlow<AppUpdateInfo?>(null)
    val updateInfo = _updateInfo.asStateFlow()

    private val _downloadProgress = MutableStateFlow(-1f) // -1 = idle, 0..1 = progress
    val downloadProgress = _downloadProgress.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading = _isDownloading.asStateFlow()

    private val _isUpdateReadyToInstall = MutableStateFlow(false)
    val isUpdateReadyToInstall = _isUpdateReadyToInstall.asStateFlow()

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
    private const val UPDATES_SUBDIR = "apk_updates"
    private const val CONNECT_TIMEOUT = 20_000
    private const val READ_TIMEOUT = 60_000
    private const val MAX_REDIRECTS = 5
    private const val BUFFER_SIZE = 8192

    private val downloadMutex = Mutex()

    @Volatile
    private var interactiveDownloadRequested = false

    // ── Computed Properties ──

    val isUpdateAvailable: Boolean
        get() {
            val info = _updateInfo.value ?: return false
            return info.latestVersionCode > BuildConfig.VERSION_CODE && info.apkUrl.isNotBlank()
        }

    val isForceUpdate: Boolean
        get() {
            val info = _updateInfo.value ?: return false
            // Major / force update if explicitly flagged OR installed build is below minimum supported code
            return info.forceUpdate || BuildConfig.VERSION_CODE < info.minVersionCode
        }

    /**
     * Only major/force updates auto-prompt the user with an install popup.
     * Minor updates download silently in the background without interrupting playback or browsing.
     */
    fun shouldAutoPromptUpdate(): Boolean {
        return isUpdateAvailable && isForceUpdate
    }

    fun shouldShowUpdate(context: Context): Boolean {
        val info = _updateInfo.value ?: return false
        if (!isUpdateAvailable) return false
        if (isForceUpdate) return true

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val dismissedVersionCode = prefs.getInt(KEY_DISMISSED_VERSION_CODE, 0)
        return info.latestVersionCode > dismissedVersionCode
    }

    fun dismissUpdatePermanently(context: Context) {
        val info = _updateInfo.value ?: return
        if (isForceUpdate) return

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_DISMISSED_VERSION_CODE, info.latestVersionCode).apply()
    }

    // ── Cache / Verification Helpers ──

    private fun getUpdatesDir(context: Context): File {
        val dir = File(context.applicationContext.filesDir, UPDATES_SUBDIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun apkFileForVersion(context: Context, versionCode: Int): File {
        return File(getUpdatesDir(context), "nexiplay-v$versionCode.apk")
    }

    private fun isValidApkArchive(context: Context, file: File, expectedMinVersionCode: Int): Boolean {
        if (!file.exists() || file.length() < 64 * 1024L) return false

        // Verify ZIP/APK header ('P', 'K')
        val hasPkHeader = runCatching {
            FileInputStream(file).use { fis ->
                val header = ByteArray(2)
                fis.read(header) == 2 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte()
            }
        }.getOrDefault(false)
        if (!hasPkHeader) return false

        // Verify package identity & version code matches NexiPlay
        return runCatching {
            val pm = context.applicationContext.packageManager
            val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(file.absolutePath, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(file.absolutePath, 0)
            } ?: return@runCatching false

            val archiveVersion = PackageInfoCompat.getLongVersionCode(pkgInfo)
            pkgInfo.packageName == context.packageName && archiveVersion >= expectedMinVersionCode.toLong()
        }.getOrDefault(false)
    }

    fun getVerifiedCachedApk(context: Context, info: AppUpdateInfo? = _updateInfo.value): File? {
        if (info == null || info.latestVersionCode <= BuildConfig.VERSION_CODE) {
            _isUpdateReadyToInstall.value = false
            return null
        }
        val candidate = apkFileForVersion(context, info.latestVersionCode)
        val valid = isValidApkArchive(context, candidate, info.latestVersionCode)
        _isUpdateReadyToInstall.value = valid
        if (valid && _totalBytes.value <= 0L) {
            _totalBytes.value = candidate.length()
        }
        return if (valid) candidate else null
    }

    fun cleanupStaleApks(context: Context, keepVersionCode: Int? = null) {
        val dirs = listOf(
            File(context.applicationContext.filesDir, UPDATES_SUBDIR),
            File(context.applicationContext.cacheDir, UPDATES_SUBDIR),
        )
        val keepName = keepVersionCode?.takeIf { it > BuildConfig.VERSION_CODE }?.let { "nexiplay-v$it.apk" }
        dirs.forEach { dir ->
            if (!dir.exists()) return@forEach
            dir.listFiles()?.forEach { file ->
                if (keepName != null && file.name == keepName && isValidApkArchive(context, file, keepVersionCode)) {
                    return@forEach
                }
                if (!downloadMutex.isLocked || !file.name.endsWith(".tmp")) {
                    runCatching { file.delete() }
                }
            }
        }
    }

    // ── Check for Update ──

    /**
     * Fetch latest update info from Supabase (`app_config` table with id = 'app_update').
     * Also verifies whether the target APK is already silently cached on disk.
     */
    suspend fun checkForUpdate(context: Context? = null) {
        withContext(Dispatchers.IO) {
            try {
                val info = SupabaseClient.main.from("app_config")
                    .select {
                        filter { eq("id", "app_update") }
                    }
                    .decodeSingleOrNull<AppUpdateInfo>()

                _updateInfo.value = info
                if (context != null) {
                    val activeTarget = info?.latestVersionCode?.takeIf { it > BuildConfig.VERSION_CODE }
                    cleanupStaleApks(context, activeTarget)
                    getVerifiedCachedApk(context, info)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _updateInfo.value = null
                _isUpdateReadyToInstall.value = false
            }
        }
    }

    // ── Silent Background Prefetch & Interactive Install ──

    /**
     * Silently downloads the update APK in background when internet is available,
     * without showing dialogs or error popups. Once complete, the APK is verified
     * and ready for instant 1-tap install without waiting.
     */
    suspend fun prefetchIfAvailable(context: Context): Boolean {
        val appContext = context.applicationContext
        if (_updateInfo.value == null) {
            checkForUpdate(appContext)
        }
        val info = _updateInfo.value ?: return false
        if (!isUpdateAvailable) {
            cleanupStaleApks(appContext, null)
            _isUpdateReadyToInstall.value = false
            return false
        }

        if (getVerifiedCachedApk(appContext, info) != null) {
            return true
        }

        return downloadApkToDisk(appContext, info, publishUiState = false) != null
    }

    /**
     * Interactive update trigger (from Force Update popup or Settings -> Update Ready).
     * If already silently downloaded in background, opens Android PackageInstaller immediately.
     */
    suspend fun downloadAndInstall(context: Context) {
        val appContext = context.applicationContext
        val info = _updateInfo.value ?: return
        if (info.apkUrl.isBlank()) {
            _errorMessage.value = "No download URL configured"
            _updateState.value = UpdateState.ERROR
            return
        }

        // Check install permission on Android 8+ before launching installer
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!appContext.packageManager.canRequestPackageInstalls()) {
                withContext(Dispatchers.Main) {
                    try {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:${appContext.packageName}")
                        ).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        appContext.startActivity(intent)
                        Toast.makeText(
                            appContext,
                            "Please allow app installation, then tap Install again",
                            Toast.LENGTH_LONG
                        ).show()
                    } catch (e: Exception) {
                        Toast.makeText(
                            appContext,
                            "Please enable 'Install unknown apps' in Settings",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                return
            }
        }

        interactiveDownloadRequested = true
        try {
            val cached = withContext(Dispatchers.IO) { getVerifiedCachedApk(appContext, info) }
            val apkFile = if (cached != null) {
                _downloadProgress.value = 1f
                _updateState.value = UpdateState.INSTALLING
                cached
            } else {
                downloadApkToDisk(appContext, info, publishUiState = true)
            } ?: return

            withContext(Dispatchers.Main) {
                installApk(appContext, apkFile)
                _updateState.value = UpdateState.COMPLETE
            }
        } finally {
            interactiveDownloadRequested = false
        }
    }

    private suspend fun downloadApkToDisk(
        context: Context,
        info: AppUpdateInfo,
        publishUiState: Boolean,
    ): File? = withContext(Dispatchers.IO) {
        downloadMutex.withLock {
            val shouldExposeUi = publishUiState || interactiveDownloadRequested

            // Re-check inside lock in case background prefetch just finished
            getVerifiedCachedApk(context, info)?.let { existing ->
                if (shouldExposeUi) {
                    _downloadProgress.value = 1f
                    _totalBytes.value = existing.length()
                    _downloadedBytes.value = existing.length()
                }
                return@withLock existing
            }

            if (shouldExposeUi) {
                _isDownloading.value = true
                _downloadProgress.value = 0f
                _errorMessage.value = null
                _updateState.value = UpdateState.CONNECTING
                _downloadedBytes.value = 0L
                _totalBytes.value = 0L
            }

            val dir = getUpdatesDir(context)
            val tmpFile = File(dir, "nexiplay-v${info.latestVersionCode}.apk.tmp")
            val finalFile = apkFileForVersion(context, info.latestVersionCode)
            var connection: HttpURLConnection? = null

            try {
                if (tmpFile.exists()) tmpFile.delete()

                val finalUrl = resolveDownloadUrl(info.apkUrl)
                val url = URL(finalUrl)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = CONNECT_TIMEOUT
                    readTimeout = READ_TIMEOUT
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "NexiPlay/${BuildConfig.VERSION_NAME}")
                }
                connection.connect()

                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    throw Exception("Server returned HTTP $responseCode")
                }

                val contentType = connection.contentType ?: ""
                if (contentType.contains("text/html", ignoreCase = true)) {
                    throw Exception("Download URL returned HTML instead of APK. Please use a direct download link.")
                }

                val totalSize = connection.contentLength.toLong()
                var downloadedSize = 0L

                if (shouldExposeUi || interactiveDownloadRequested) {
                    _totalBytes.value = totalSize
                    _updateState.value = UpdateState.DOWNLOADING
                }

                connection.inputStream.use { input ->
                    tmpFile.outputStream().use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedSize += bytesRead
                            if (publishUiState || interactiveDownloadRequested) {
                                _isDownloading.value = true
                                _downloadedBytes.value = downloadedSize
                                _totalBytes.value = totalSize
                                if (_updateState.value != UpdateState.DOWNLOADING) {
                                    _updateState.value = UpdateState.DOWNLOADING
                                }
                                if (totalSize > 0) {
                                    _downloadProgress.value = downloadedSize.toFloat() / totalSize.toFloat()
                                }
                            }
                        }
                    }
                }

                if (totalSize > 0 && downloadedSize != totalSize) {
                    throw Exception("Download incomplete or file corrupted")
                }
                if (!isValidApkArchive(context, tmpFile, info.latestVersionCode)) {
                    throw Exception("Downloaded package failed verification or is corrupted")
                }

                // Remove old APK versions only after the new APK has passed verification
                dir.listFiles()?.forEach { file ->
                    if (file.name != tmpFile.name && file.name != finalFile.name) {
                        runCatching { file.delete() }
                    }
                }
                if (finalFile.exists()) finalFile.delete()
                if (!tmpFile.renameTo(finalFile)) {
                    tmpFile.copyTo(finalFile, overwrite = true)
                    tmpFile.delete()
                }

                _isUpdateReadyToInstall.value = true
                _totalBytes.value = finalFile.length()

                if (publishUiState || interactiveDownloadRequested) {
                    _downloadProgress.value = 1f
                    _updateState.value = UpdateState.INSTALLING
                }

                finalFile
            } catch (e: Exception) {
                e.printStackTrace()
                runCatching { if (tmpFile.exists()) tmpFile.delete() }
                _isUpdateReadyToInstall.value = false

                if (publishUiState || interactiveDownloadRequested) {
                    val userMessage = when {
                        e.message?.contains("timeout", ignoreCase = true) == true ->
                            "Connection timed out. Please check your internet and try again."
                        e.message?.contains("HTML", ignoreCase = true) == true ->
                            "The update link is unavailable. Please try again later."
                        e.message?.contains("HTTP", ignoreCase = true) == true ->
                            "The update server couldn't be reached. Please try again later."
                        e.message?.contains("Unable to resolve host", ignoreCase = true) == true ->
                            "No internet connection. Please check your network."
                        e.message?.contains("corrupted", ignoreCase = true) == true ->
                            "Download was incomplete. Please try again."
                        else ->
                            "Update download failed. Check your connection and available storage, then try again."
                    }
                    _errorMessage.value = userMessage
                    _downloadProgress.value = -1f
                    _updateState.value = UpdateState.ERROR
                } else {
                    _downloadProgress.value = -1f
                    _updateState.value = UpdateState.IDLE
                    _errorMessage.value = null
                }
                null
            } finally {
                runCatching { connection?.disconnect() }
                if (publishUiState || interactiveDownloadRequested) {
                    _isDownloading.value = false
                }
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

        if (currentUrl.contains("drive.google.com") || currentUrl.contains("docs.google.com")) {
            val fileId = extractGDriveFileId(currentUrl)
            if (fileId != null) {
                currentUrl = "https://drive.google.com/uc?export=download&id=$fileId&confirm=t"
            }
        }

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

            if (code == 200 && (currentUrl.contains("drive.google.com") || currentUrl.contains("docs.google.com"))) {
                val contentType = conn.contentType ?: ""
                if (contentType.contains("text/html")) {
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

    private fun extractGDriveFileId(url: String): String? {
        val pattern1 = Regex("/file/d/([a-zA-Z0-9_-]+)")
        pattern1.find(url)?.groupValues?.get(1)?.let { return it }

        val pattern2 = Regex("[?&]id=([a-zA-Z0-9_-]+)")
        pattern2.find(url)?.groupValues?.get(1)?.let { return it }

        val pattern3 = Regex("open\\?id=([a-zA-Z0-9_-]+)")
        pattern3.find(url)?.groupValues?.get(1)?.let { return it }

        return null
    }

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
            _errorMessage.value = "Couldn't open the installer. Check that installs from this source are allowed, then try again."
            _updateState.value = UpdateState.ERROR
        }
    }

    // ── State Management ──

    fun resetState() {
        _downloadProgress.value = -1f
        _isDownloading.value = false
        _errorMessage.value = null
        _updateState.value = UpdateState.IDLE
        _downloadedBytes.value = 0L
        _totalBytes.value = 0L
    }

    fun dismissUpdate() {
        resetState()
    }

    fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
            else -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }
}
