package com.nanzstream.nanas

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.nanzstream.nanas.data.local.OfflineMangaManager
import com.nanzstream.nanas.data.local.StorageManager
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class NanzStreamApp : Application(), ImageLoaderFactory {

    companion object {
        lateinit var instance: NanzStreamApp
            private set
        lateinit var storage: StorageManager
            private set
        lateinit var offlineManga: OfflineMangaManager
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        storage = StorageManager(this)
        offlineManga = OfflineMangaManager(this)
        coil.Coil.setImageLoader(newImageLoader())
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.05)
                    .build()
            }
            .okHttpClient {
                OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        val req = chain.request()
                        val urlStr = req.url.toString()
                        val builder = req.newBuilder()
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                        if (urlStr.contains("webtoon") || urlStr.contains("pstatic.net")) {
                            builder.header("Referer", "https://www.webtoons.com/")
                        } else if (urlStr.contains("samehadaku")) {
                            builder.header("Referer", "https://samehadaku.li/")
                        } else if (urlStr.contains("anichin")) {
                            builder.header("Referer", "https://anichin.ro/")
                        }
                        chain.proceed(builder.build())
                    }
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build()
            }
            .respectCacheHeaders(false)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .crossfade(true)
            .build()
    }
}
