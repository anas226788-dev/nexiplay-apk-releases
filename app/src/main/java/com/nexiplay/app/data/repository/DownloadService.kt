package com.nexiplay.app.data.repository

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.DocumentsContract
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.io.OutputStream

class DownloadService : Service() {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val client = OkHttpClient()

    companion object {
        const val CHANNEL_ID = "nexiplay_downloads"
        const val ACTION_START_DOWNLOAD = "start_download"
        const val EXTRA_URL = "url"
        const val EXTRA_FILENAME = "filename"
        const val EXTRA_TITLE = "title"

        fun start(context: Context, url: String, fileName: String, title: String) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_START_DOWNLOAD
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_FILENAME, fileName)
                putExtra(EXTRA_TITLE, title)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private val appIconBitmap: android.graphics.Bitmap? by lazy {
        try {
            android.graphics.BitmapFactory.decodeResource(resources, com.nexiplay.app.R.mipmap.ic_launcher)
        } catch (_: Exception) { null }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "NexiPlay Downloads",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START_DOWNLOAD) {
            val url = intent.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
            val fileName = intent.getStringExtra(EXTRA_FILENAME) ?: "video.mp4"
            val title = intent.getStringExtra(EXTRA_TITLE) ?: "Downloading"

            val notificationId = url.hashCode()

            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText("Downloading...")
                .setSmallIcon(com.nexiplay.app.R.drawable.ic_stat_onesignal_default)
                .apply { if (appIconBitmap != null) setLargeIcon(appIconBitmap) }
                .setColor(0xFFDC2626.toInt())
                .setProgress(100, 0, true)
                .build()

            startForeground(notificationId, notification)

            scope.launch {
                performDownload(url, fileName, title, notificationId)
            }
        }
        return START_STICKY
    }

    private suspend fun performDownload(url: String, fileName: String, title: String, notificationId: Int) {
        val settings = SettingsRepository(this)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val sdCardUriStr = settings.sdCardUri
        if (sdCardUriStr == null) {
            stopForeground(true)
            stopSelf()
            return
        }

        val treeUri = Uri.parse(sdCardUriStr)
        val pickedDir = DocumentFile.fromTreeUri(this, treeUri)
        if (pickedDir == null || !pickedDir.canWrite()) {
            stopForeground(true)
            stopSelf()
            return
        }

        // Create file
        val file = pickedDir.createFile("video/mp4", fileName)
        if (file == null) {
            stopForeground(true)
            stopSelf()
            return
        }

        val outStream: OutputStream? = try {
            contentResolver.openOutputStream(file.uri)
        } catch (e: Exception) {
            null
        }

        if (outStream == null) {
            stopForeground(true)
            stopSelf()
            return
        }

        try {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                outStream.close()
                throw Exception("Failed to download")
            }

            val body = response.body
            if (body == null) {
                outStream.close()
                throw Exception("Empty response body")
            }

            val totalBytes = body.contentLength()
            val inStream: InputStream = body.byteStream()
            val buffer = ByteArray(8 * 1024)
            var bytesCopied: Long = 0
            var lastUpdate = System.currentTimeMillis()

            var bytes = inStream.read(buffer)
            while (bytes >= 0) {
                outStream.write(buffer, 0, bytes)
                bytesCopied += bytes

                val now = System.currentTimeMillis()
                if (now - lastUpdate > 1000) { // Update notification every second
                    val progress = if (totalBytes > 0) ((bytesCopied * 100) / totalBytes).toInt() else 0
                    val notif = NotificationCompat.Builder(this@DownloadService, CHANNEL_ID)
                        .setContentTitle(title)
                        .setContentText("Downloading... $progress%")
                        .setSmallIcon(com.nexiplay.app.R.drawable.ic_stat_onesignal_default)
                        .apply { if (appIconBitmap != null) setLargeIcon(appIconBitmap) }
                        .setColor(0xFFDC2626.toInt())
                        .setProgress(100, progress, totalBytes <= 0)
                        .build()
                    nm.notify(notificationId, notif)
                    lastUpdate = now
                }
                bytes = inStream.read(buffer)
            }

            outStream.flush()
            outStream.close()
            inStream.close()

            // Success
            val notif = NotificationCompat.Builder(this@DownloadService, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText("Download Complete")
                .setSmallIcon(com.nexiplay.app.R.drawable.ic_stat_onesignal_default)
                .apply { if (appIconBitmap != null) setLargeIcon(appIconBitmap) }
                .setColor(0xFF10B981.toInt())
                .build()
            nm.notify(notificationId, notif)

        } catch (e: Exception) {
            e.printStackTrace()
            file.delete()
            val notif = NotificationCompat.Builder(this@DownloadService, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText("Download Failed")
                .setSmallIcon(com.nexiplay.app.R.drawable.ic_stat_onesignal_default)
                .apply { if (appIconBitmap != null) setLargeIcon(appIconBitmap) }
                .setColor(0xFFEF4444.toInt())
                .build()
            nm.notify(notificationId, notif)
        } finally {
            stopForeground(false)
        }
    }
}
