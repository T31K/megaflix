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

    /**
     * Fetch TMDB art/metadata for entries that haven't been looked up yet.
     * A row is marked tmdbChecked even on a miss so we only ever query once
     * per file. Runs after scan; UI updates arrive via the existing Flow.
     */
    suspend fun enrichMissing() {
        for (v in dao.unenriched()) {
            val match = TmdbClient.search(
                title = v.title,
                year = v.year,
                isTv = v.season != null,
            )
            dao.update(
                if (match == null) v.copy(tmdbChecked = true)
                else v.copy(
                    tmdbChecked = true,
                    tmdbId = match.tmdbId,
                    posterPath = match.posterPath,
                    backdropPath = match.backdropPath,
                    overview = match.overview,
                    rating = match.rating,
                    genres = match.genres,
                )
            )
        }
    }

    fun observeLibrary(): Flow<List<VideoEntity>> = dao.observeAll()
    suspend fun getByUri(uri: String) = dao.getByUri(uri)
    suspend fun byId(id: Long) = dao.byId(id)
    suspend fun saveProgress(uri: String, positionMs: Long, durationMs: Long) =
        dao.updateProgress(uri, positionMs, durationMs)
}
