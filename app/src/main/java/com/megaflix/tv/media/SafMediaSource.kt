package com.megaflix.tv.media

import android.content.Context
import android.net.Uri

/**
 * PHASE 4 FALLBACK — used if all-files access is refused. The user picks the
 * USB drive's folder via ACTION_OPEN_DOCUMENT_TREE; we persist the treeUri and
 * enumerate through DocumentFile.
 *
 * The body is intentionally stubbed in Phase 1 to keep the dependency set
 * minimal. To enable in Phase 4:
 *   1. Uncomment `androidx.documentfile:documentfile:1.0.1` in app/build.gradle.kts.
 *   2. Replace the stubbed methods below with the DocumentFile implementation
 *      (recursive walk over treeUri, same VideoFile mapping as UsbHddSource,
 *      filtering by VIDEO_EXTENSIONS).
 */
class SafMediaSource(
    private val context: Context,
    private val treeUri: Uri,
) : MediaSource {

    override suspend fun listVideoFiles(): List<VideoFile> =
        TODO("Phase 4: enumerate DocumentFile tree under treeUri")

    override fun openPlayableUri(file: VideoFile): Uri = Uri.parse(file.uri)
}
