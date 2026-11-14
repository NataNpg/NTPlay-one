package com.ntplayone.tv

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache

class NtApp : Application(), ImageLoaderFactory {
    lateinit var model: AppModel

    override fun onCreate() {
        super.onCreate()
        model = AppModel(this)
        model.load()
        PairingServer(model).start()
    }

    // sem crossfade e com cache limitado: menos trabalho na thread principal
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .crossfade(false)
            .respectCacheHeaders(false)
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.15).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("logos"))
                    .maxSizeBytes(64L * 1024 * 1024)
                    .build()
            }
            .build()
}