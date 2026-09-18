package com.megaflix.tv.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.megaflix.tv.App
import com.megaflix.tv.R
import com.megaflix.tv.data.TmdbClient
import com.megaflix.tv.data.VideoEntity
import kotlinx.coroutines.launch

/**
 * Jellyfin-style detail screen: full-bleed backdrop under a scrim, title +
 * badges + overview on the left, poster on the right, cast row at the bottom.
 */
class DetailActivity : AppCompatActivity() {

    private val repo by lazy { (application as App).graph.repository }
    private val castAdapter = CastAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)

        val castRow = findViewById<RecyclerView>(R.id.cast_row)
        castRow.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        castRow.adapter = castAdapter

        val id = intent.getLongExtra(EXTRA_ID, -1)
        lifecycleScope.launch {
            val video = repo.byId(id) ?: run { finish(); return@launch }
            bind(video)
        }
    }

    private fun bind(v: VideoEntity) {
        findViewById<ImageView>(R.id.backdrop).load(TmdbClient.backdropUrl(v.backdropPath))
        findViewById<ImageView>(R.id.poster).load(TmdbClient.posterUrl(v.posterPath))
        findViewById<TextView>(R.id.title).text = v.title
        findViewById<TextView>(R.id.genres).text = v.genres ?: ""
        findViewById<TextView>(R.id.overview).text = v.overview ?: ""

        findViewById<TextView>(R.id.badge_rating).apply {
            visibility = if (v.rating != null) View.VISIBLE else View.GONE
            text = v.rating?.let { "★ %.1f".format(it) }
        }
        findViewById<TextView>(R.id.badge_year).apply {
            visibility = if (v.year != null) View.VISIBLE else View.GONE
            text = v.year?.toString()
        }
        findViewById<TextView>(R.id.badge_meta).apply {
            val meta = when {
                v.season != null && v.episode != null -> "S%02dE%02d".format(v.season, v.episode)
                v.runtimeMin != null -> "${v.runtimeMin} min"
                else -> null
            }
            visibility = if (meta != null) View.VISIBLE else View.GONE
            text = meta
        }

        val play = findViewById<Button>(R.id.btn_play)
        val resume = findViewById<Button>(R.id.btn_resume)
        play.setOnClickListener { startPlayer(v, fromStart = true) }
        if (v.positionMs > 0) {
            resume.visibility = View.VISIBLE
            resume.text = getString(R.string.detail_resume)
            resume.setOnClickListener { startPlayer(v, fromStart = false) }
            resume.requestFocus()
        } else {
            play.requestFocus()
        }

        val tmdbId = v.tmdbId
        if (tmdbId != null) lifecycleScope.launch {
            castAdapter.submit(TmdbClient.credits(tmdbId, isTv = v.season != null))
        }
    }

    private fun startPlayer(v: VideoEntity, fromStart: Boolean) {
        startActivity(
            Intent(this, PlayerActivity::class.java)
                .putExtra(PlayerActivity.EXTRA_URI, v.uri)
                .putExtra(PlayerActivity.EXTRA_FROM_START, fromStart)
        )
    }

    companion object {
        const val EXTRA_ID = "extra_id"
    }
}
