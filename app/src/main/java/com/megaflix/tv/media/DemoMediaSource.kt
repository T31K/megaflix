package com.megaflix.tv.media

import android.content.Context
import android.net.Uri

/**
 * DEMO source — used when BuildConfig.DEMO_MODE is true. Returns a fixed set of
 * poster cards that all play the bundled 5-second clip (res/raw/hotd_sample.mp4),
 * so the shareable APK works on a fresh TV with no media pushed and no permission.
 *
 * Each card gets a distinct "demo://" uri (Room requires unique uris); playback
 * resolves them all to the bundled raw resource — see PlayerActivity.
 */
class DemoMediaSource(private val context: Context) : MediaSource {

    override suspend fun listVideoFiles(): List<VideoFile> = listOf(
        demo("demo://hotd", "House.of.the.Dragon.S03E05.1080p.HEVC.x265-MeGusta.mkv"),
        demo("demo://spacejam", "Space.Jam.1996.720p.mp4"),
        demo("demo://matrix", "The.Matrix.1999.1080p.mp4"),
        demo("demo://inception", "Inception.2010.1080p.mp4"),
        demo("demo://interstellar", "Interstellar.2014.1080p.mp4"),
        demo("demo://darkknight", "The.Dark.Knight.2008.1080p.mp4"),
        demo("demo://spirited", "Spirited.Away.2001.1080p.mp4"),
        demo("demo://pulpfiction", "Pulp.Fiction.1994.1080p.mp4"),
        demo("demo://forrestgump", "Forrest.Gump.1994.1080p.mp4"),
        demo("demo://gladiator", "Gladiator.2000.1080p.mp4"),
        demo("demo://fightclub", "Fight.Club.1999.1080p.mp4"),
        demo("demo://shawshank", "The.Shawshank.Redemption.1994.1080p.mp4"),
    )

    private fun demo(uri: String, filename: String) =
        VideoFile(uri = uri, filename = filename, sizeBytes = 0, modifiedEpochSec = 0)

    override fun openPlayableUri(file: VideoFile): Uri = bundledClipUri(context)

    companion object {
        /** The single bundled clip every demo card plays. */
        fun bundledClipUri(context: Context): Uri =
            Uri.parse("android.resource://${context.packageName}/raw/hotd_sample")
    }
}
