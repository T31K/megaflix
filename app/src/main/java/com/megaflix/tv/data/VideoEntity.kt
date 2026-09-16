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
)
