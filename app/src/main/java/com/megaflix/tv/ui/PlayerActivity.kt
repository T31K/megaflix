package com.megaflix.tv.ui

import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.megaflix.tv.App
import com.megaflix.tv.R
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class PlayerActivity : AppCompatActivity() {

    private val repo by lazy { (application as App).graph.repository }
    private var player: ExoPlayer? = null
    private lateinit var uri: String
    private var startPositionMs: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        uri = intent.getStringExtra(EXTRA_URI) ?: run { finish(); return }
        lifecycleScope.launch {
            val existing = repo.getByUri(uri)
            // Resume unless we were within ~5s of the end (treat as finished).
            startPositionMs = existing
                ?.takeIf { it.durationMs == 0L || it.positionMs < it.durationMs - 5000 }
                ?.positionMs ?: 0
            preparePlayer()
        }
    }

    private fun preparePlayer() {
        val exo = ExoPlayer.Builder(this).build()
        findViewById<PlayerView>(R.id.player).player = exo
        exo.setMediaItem(MediaItem.fromUri(Uri.parse(uri)))
        if (startPositionMs > 0) exo.seekTo(startPositionMs)
        exo.playWhenReady = true
        exo.prepare()
        player = exo
    }

    override fun onStop() {
        super.onStop()
        player?.let { p ->
            val pos = p.currentPosition
            val dur = if (p.duration > 0) p.duration else 0
            // Blocking save so progress persists before teardown; single indexed UPDATE.
            runBlocking { repo.saveProgress(uri, pos, dur) }
            p.release()
        }
        player = null
    }

    companion object {
        const val EXTRA_URI = "extra_uri"
    }
}
