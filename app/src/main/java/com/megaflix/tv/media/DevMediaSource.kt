package com.megaflix.tv.media

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Phase 1 source. Reads whatever the emulator has indexed under shared
 * storage (we push files into Movies/ then trigger a media scan — see
 * push-test-media.sh). MediaStore avoids raw /sdcard File reads, which
 * scoped storage blocks on API 30+ without all-files access.
 */
class DevMediaSource(private val context: Context) : MediaSource {

    override suspend fun listVideoFiles(): List<VideoFile> = withContext(Dispatchers.IO) {
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_MODIFIED,
        )
        val result = ArrayList<VideoFile>()
        context.contentResolver.query(
            collection, projection, null, null,
            "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val modCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
            while (c.moveToNext()) {
                val name = c.getString(nameCol) ?: continue
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext !in VIDEO_EXTENSIONS) continue
                val id = c.getLong(idCol)
                val contentUri = ContentUris.withAppendedId(collection, id)
                result += VideoFile(
                    uri = contentUri.toString(),
                    filename = name,
                    sizeBytes = c.getLong(sizeCol),
                    modifiedEpochSec = c.getLong(modCol),
                )
            }
        }
        result
    }

    override fun openPlayableUri(file: VideoFile): Uri = Uri.parse(file.uri)
}
