package com.megaflix.tv

import android.content.Context
import com.megaflix.tv.data.LibraryDatabase
import com.megaflix.tv.data.LibraryRepository
import com.megaflix.tv.media.DemoMediaSource
import com.megaflix.tv.media.DevMediaSource
import com.megaflix.tv.media.MediaSource
import com.megaflix.tv.media.UsbHddSource

/** Manual DI. No framework. */
class Graph(appContext: Context) {
    val db = LibraryDatabase.get(appContext)
    val videoDao = db.videoDao()

    // The swap point. DEMO_MODE ships in the shareable APK; Phase 4 flips
    // USE_USB_SOURCE for the real drive. Otherwise DevMediaSource (emulator).
    val source: MediaSource = when {
        BuildConfig.DEMO_MODE -> DemoMediaSource(appContext)
        BuildConfig.USE_USB_SOURCE -> UsbHddSource(appContext)
        else -> DevMediaSource(appContext)
    }

    val repository = LibraryRepository(source, videoDao)
}
