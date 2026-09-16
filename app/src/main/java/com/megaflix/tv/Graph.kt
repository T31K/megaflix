package com.megaflix.tv

import android.content.Context
import com.megaflix.tv.data.LibraryDatabase
import com.megaflix.tv.data.LibraryRepository
import com.megaflix.tv.media.DevMediaSource
import com.megaflix.tv.media.MediaSource
import com.megaflix.tv.media.UsbHddSource

/** Manual DI. No framework. */
class Graph(appContext: Context) {
    val db = LibraryDatabase.get(appContext)
    val videoDao = db.videoDao()

    // The swap point. Phase 4 flips USE_USB_SOURCE; UsbHddSource is dormant now.
    val source: MediaSource =
        if (BuildConfig.USE_USB_SOURCE) UsbHddSource(appContext)
        else DevMediaSource(appContext)

    val repository = LibraryRepository(source, videoDao)
}
