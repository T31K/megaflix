package com.megaflix.tv.media

/** One video on some backing store. [uri] is a string so it persists in Room directly. */
data class VideoFile(
    val uri: String,
    val filename: String,
    val sizeBytes: Long,
    val modifiedEpochSec: Long,
)

/** Lowercase, no leading dot. */
val VIDEO_EXTENSIONS: Set<String> = setOf("mp4", "mkv", "avi", "mov", "webm")
