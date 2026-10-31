package com.nexiplay.app

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.allowRgb565
import coil3.request.bitmapConfig
import coil3.request.crossfade
import com.onesignal.OneSignal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okio.Path.Companion.toPath
import java.util.concurrent.TimeUnit

class NexiPlayApp : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize AdManager & Ad SDKs on background IO thread
        CoroutineScope(Dispatchers.IO).launch {
            com.nexiplay.app.data.util.AdManager.loadConfig(com.nexiplay.app.data.SupabaseClient.main)
            withContext(Dispatchers.Main) {
                // Initialize Start.io (if configured)
                if (com.nexiplay.app.data.util.AdManager.startioAppId.isNotEmpty()) {
                    com.nexiplay.app.data.util.AdManager.initStartIo(this@NexiPlayApp, com.nexiplay.app.data.util.AdManager.startioAppId)
                }
            }
        }
        
        // Initialize OneSignal
        OneSignal.initWithContext(this, "69822dc3-4533-4b09-b105-d0ddaeb22c1f")
    }

    // High-performance unified image caching engine for 60/120fps smooth scrolling
    // Tuned specifically to avoid OutOfMemoryError and UI jank on 2GB RAM devices
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val isLowRam = activityManager?.isLowRamDevice == true

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient }))
            }
            .memoryCache {
                MemoryCache.Builder()
                    // 12% RAM allocation on low-RAM (2GB) phones prevents GC thrashing; 25% on normal phones
                    .maxSizePercent(context, if (isLowRam) 0.12 else 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache").absolutePath.toPath())
                    .maxSizeBytes(if (isLowRam) 100L * 1024 * 1024 else 200L * 1024 * 1024)
                    .build()
            }
            // RGB_565 decodes pixels as 2 bytes (16-bit) instead of 4 bytes (32-bit), reducing memory by 50%
            .allowRgb565(isLowRam)
            .apply {
                if (isLowRam) {
                    bitmapConfig(Bitmap.Config.RGB_565)
                    crossfade(100)
                } else {
                    crossfade(200)
                }
            }
            .build()
    }
}
