package com.linkstreamz.app

import android.content.Context
import coil.ImageLoader

object AppImages {

    @Volatile
    private var directLoader: ImageLoader? = null

    fun direct(context: Context): ImageLoader =
        directLoader ?: synchronized(this) {
            directLoader ?: ImageLoader.Builder(context.applicationContext)
                .okHttpClient(HttpClients.direct)
                .crossfade(true)
                .build()
                .also { directLoader = it }
        }
}
