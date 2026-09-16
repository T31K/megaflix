package com.megaflix.tv.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * PHASE 4 SOURCE — compiled now, never instantiated while USE_USB_SOURCE=false.
 *
 * Reads video files directly off the removable USB HDD plugged into the TCL TV.
 * Two things it must handle on real hardware:
 *   1. Finding the removable volume among StorageManager.storageVolumes.
 *   2. Getting read access — MANAGE_EXTERNAL_STORAGE (all-files) is fine for a
 *      sideloaded personal app; SafMediaSource is the folder-picker fallback.
 */
class UsbHddSource(private val context: Context) : MediaSource {

    /** True once the user has granted all-files access in system settings. */
    fun hasAllFilesAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager()
        else true // pre-R: covered by READ_EXTERNAL_STORAGE granted at install/runtime

    /** Intent to send the user to the all-files-access toggle for this app. */
    fun requestAllFilesAccessIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            .setData(Uri.parse("package:${context.packageName}"))

    override suspend fun listVideoFiles(): List<VideoFile> = withContext(Dispatchers.IO) {
        val root = findRemovableVolumeRoot() ?: return@withContext emptyList()
        val out = ArrayList<VideoFile>()
        // Recursive walk; USB drives are shallow enough that a plain walk is fine.
        root.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in VIDEO_EXTENSIONS }
            .forEach { f ->
                out += VideoFile(
                    uri = Uri.fromFile(f).toString(),
                    filename = f.name,
                    sizeBytes = f.length(),
                    modifiedEpochSec = f.lastModified() / 1000,
                )
            }
        out
    }

    /**
     * Picks the first removable, mounted volume's directory. On the TCL the USB
     * HDD shows up here once mounted; internal storage is filtered by isRemovable.
     * Uses StorageVolume.directory on API 30+, reflection-free.
     */
    private fun findRemovableVolumeRoot(): File? {
        val sm = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        for (vol in sm.storageVolumes) {
            if (!vol.isRemovable) continue
            if (vol.state != Environment.MEDIA_MOUNTED) continue
            val dir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) vol.directory else null
            if (dir != null) return dir
        }
        return null
    }

    override fun openPlayableUri(file: VideoFile): Uri = Uri.parse(file.uri)
}
