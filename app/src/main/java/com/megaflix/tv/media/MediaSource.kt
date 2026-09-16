package com.megaflix.tv.media

import android.net.Uri

/**
 * The swap point between the Mac test build (DevMediaSource) and the real
 * TCL/USB build (UsbHddSource). Graph picks one via BuildConfig.USE_USB_SOURCE.
 */
interface MediaSource {
    suspend fun listVideoFiles(): List<VideoFile>
    fun openPlayableUri(file: VideoFile): Uri
}
