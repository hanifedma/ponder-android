package com.hanifedma.ponder

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import okio.Path.Companion.toOkioPath

/**
 * Firebase initialises itself from google-services.json when one is present; if
 * it is not, the app simply runs device-only and nothing here has to change.
 *
 * This class exists to give inline media a properly-bounded image cache, so
 * previews survive scrolling and reloads without re-downloading — the "low
 * internet friendly" behaviour the web app aims for.
 */
class PonderApp : Application(), SingletonImageLoader.Factory {

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory()) }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("media").toOkioPath())
                    .maxSizeBytes(48L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .build()
}
