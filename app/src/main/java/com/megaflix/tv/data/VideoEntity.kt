package com.megaflix.tv.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "videos", indices = [Index(value = ["uri"], unique = true)])
data class VideoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    val filename: String,
    val title: String,
    val year: Int?,
    val season: Int?,
    val episode: Int?,
    val sizeBytes: Long,
    val modifiedEpochSec: Long,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val addedEpochSec: Long,
    // TMDB enrichment (Phase 2). tmdbChecked=true once a lookup ran (hit or miss)
    // so we never re-query for the same file.
    val tmdbId: Long? = null,
    val posterPath: String? = null,     // e.g. "/abc.jpg" — prepend image base URL
    val backdropPath: String? = null,
    val overview: String? = null,
    val rating: Double? = null,         // TMDB vote_average, 0..10
    val runtimeMin: Int? = null,
    val genres: String? = null,         // display-ready, e.g. "Action / Sci-Fi"
    val tmdbChecked: Boolean = false,
)
