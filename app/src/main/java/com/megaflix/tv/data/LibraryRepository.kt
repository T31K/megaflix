package com.megaflix.tv.data

import com.megaflix.tv.media.FilenameParser
import com.megaflix.tv.media.MediaSource
import kotlinx.coroutines.flow.Flow

class LibraryRepository(
    private val source: MediaSource,
    private val dao: VideoDao,
) {
    /** Scans the source, inserts unseen files, returns how many were new. */
    suspend fun scan(): Int {
        val files = source.listVideoFiles()
        val known = dao.allUris().toHashSet()
        val now = System.currentTimeMillis() / 1000
        val fresh = files.filter { it.uri !in known }.map { f ->
            val p = FilenameParser.parse(f.filename)
            VideoEntity(
                uri = f.uri,
                filename = f.filename,
                title = p.title,
                year = p.year,
                season = p.season,
                episode = p.episode,
                sizeBytes = f.sizeBytes,
                modifiedEpochSec = f.modifiedEpochSec,
                addedEpochSec = now,
            )
        }
        if (fresh.isNotEmpty()) dao.insertNew(fresh)
        return fresh.size
    }

    fun observeLibrary(): Flow<List<VideoEntity>> = dao.observeAll()
    suspend fun getByUri(uri: String) = dao.getByUri(uri)
    suspend fun saveProgress(uri: String, positionMs: Long, durationMs: Long) =
        dao.updateProgress(uri, positionMs, durationMs)
}
