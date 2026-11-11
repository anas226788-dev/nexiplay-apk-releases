package com.nexiplay.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.allowHardware
import coil3.request.crossfade
import com.onesignal.OneSignal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okio.Path.Companion.toPath
import java.util.concurrent.TimeUnit

class NexiPlayApp : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()

        // Defer remote ad config fetch slightly so Activity & Splash Frame 1 never contend with Supabase/Ktor init
        CoroutineScope(Dispatchers.IO).launch {
            delay(350L)
            com.nexiplay.app.data.util.AdManager.loadConfig(com.nexiplay.app.data.SupabaseClient.main)
        }

        // Initialize OneSignal synchronously on main thread as required by OneSignal SDK
        try {
            OneSignal.initWithContext(this, "69822dc3-4533-4b09-b105-d0ddaeb22c1f")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // High-performance unified image caching engine for 120fps zero-jank scrolling
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val okHttpClient = OkHttpClient.Builder()
            .connectionPool(ConnectionPool(16, 5, TimeUnit.MINUTES))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                val original = chain.request()
                val host = original.url.host.lowercase()
                val builder = original.newBuilder()
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
                    )
                    .header(
                        "Accept",
                        "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8"
                    )

                if (host.contains("wikimedia.org") || host.contains("wikipedia.org")) {
                    builder.header("Referer", "https://en.wikipedia.org/")
                } else if (host.contains("wikia.nocookie.net") || host.contains("fandom.com")) {
                    builder.header("Referer", "https://www.fandom.com/")
                } else if (host.contains("bollyflix")) {
                    builder.header("Referer", "https://www.google.com/")
                }
                chain.proceed(builder.build())
            }
            .build()

        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient }))
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.28)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache").absolutePath.toPath())
                    .maxSizeBytes(250L * 1024 * 1024)
                    .build()
            }
            .allowHardware(true)
            .crossfade(false)
            .build()
    }
}
