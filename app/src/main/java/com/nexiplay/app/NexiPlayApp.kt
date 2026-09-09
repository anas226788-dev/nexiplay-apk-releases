package com.nexiplay.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
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
    override fun newImageLoader(context: PlatformContext): ImageLoader {
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
                    .maxSizePercent(context, 0.25) // 25% of RAM for instant in-memory poster rendering
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache").absolutePath.toPath())
                    .maxSizeBytes(200L * 1024 * 1024) // 200MB persistent disk cache
                    .build()
            }
            .crossfade(true)
            .build()
    }
}
