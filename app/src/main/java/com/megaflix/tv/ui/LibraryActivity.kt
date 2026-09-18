package com.megaflix.tv.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.megaflix.tv.App
import com.megaflix.tv.BuildConfig
import com.megaflix.tv.R
import com.megaflix.tv.data.VideoEntity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class LibraryActivity : AppCompatActivity() {

    private val repo by lazy { (application as App).graph.repository }
    private lateinit var rowsAdapter: RowsAdapter
    private lateinit var rows: RecyclerView
    private lateinit var empty: TextView
    private lateinit var navBar: View
    private lateinit var navTabs: List<TextView>
    private var lastNavTab: TextView? = null

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { rescanAndObserve() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_library)

        empty = findViewById(R.id.empty)
        setUpNavTabs()

        rowsAdapter = RowsAdapter { video ->
            startActivity(
                Intent(this, DetailActivity::class.java)
                    .putExtra(DetailActivity.EXTRA_ID, video.id)
            )
        }
        rows = findViewById(R.id.rows)
        rows.layoutManager = LinearLayoutManager(this)
        rows.adapter = rowsAdapter

        ensurePermissionThenScan()
    }

    private fun setUpNavTabs() {
        navBar = findViewById(R.id.nav_bar)
        val home = findViewById<TextView>(R.id.tab_home)
        home.isSelected = true // active pill
        navTabs = listOf(
            home,
            findViewById(R.id.tab_shows),
            findViewById(R.id.tab_movies),
            findViewById(R.id.tab_search),
        )
        lastNavTab = home
        // Remember which tab was last focused so UP from the grid returns there.
        // Tab filtering is Phase 2+; for now they keep D-pad focus sane.
        navTabs.forEach { tab ->
            tab.setOnFocusChangeListener { v, focused -> if (focused) lastNavTab = v as TextView }
        }
    }

    /**
     * RecyclerView won't let focus escape upward to the sibling nav bar on its
     * own, and DOWN from the nav bar needs to land on a card. Bridge both here.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val focused = currentFocus
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP ->
                    if (focused != null && focusedRowPosition() == 0) {
                        (lastNavTab ?: navTabs.first()).requestFocus()
                        return true
                    }
                KeyEvent.KEYCODE_DPAD_DOWN ->
                    if (focused != null && isInNavBar(focused)) {
                        focusFirstCard()
                        return true
                    }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun isInNavBar(view: View): Boolean {
        var p: View? = view
        while (p != null) {
            if (p === navBar) return true
            p = p.parent as? View
        }
        return false
    }

    /** The outer-row index the focused card sits in, or -1 if focus isn't in a row. */
    private fun focusedRowPosition(): Int {
        var v: View = currentFocus ?: return -1
        var parent = v.parent
        while (parent != null && parent !== rows) {
            v = parent as? View ?: return -1
            parent = v.parent
        }
        return if (parent === rows) rows.getChildAdapterPosition(v) else -1
    }

    private fun ensurePermissionThenScan() {
        if (BuildConfig.DEMO_MODE) { rescanAndObserve(); return }
        val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO
        else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED) {
            rescanAndObserve()
        } else {
            permLauncher.launch(perm)
        }
    }

    private fun rescanAndObserve() {
        lifecycleScope.launch {
            repo.scan()
            // Enrich concurrently: the grid renders now with placeholders, and
            // posters/backdrops fill in as TMDB responds (observeLibrary re-emits).
            launch { repo.enrichMissing() }
            repo.observeLibrary().collectLatest { list ->
                val built = buildRows(list)
                rowsAdapter.submit(built)
                empty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                // Land focus on the first poster once content exists.
                if (built.isNotEmpty()) rows.post { focusFirstCard() }
            }
        }
    }

    /** Group the flat library into home rows (skips empties). */
    private fun buildRows(all: List<VideoEntity>): List<Row> {
        if (all.isEmpty()) return emptyList()
        val continueWatching = all.filter { it.positionMs > 0 }
        val shows = all.filter { it.season != null }
        val movies = all.filter { it.season == null && it.year != null }
        return buildList {
            if (continueWatching.isNotEmpty()) add(Row("Continue Watching", continueWatching))
            add(Row("Recently Added", all))
            if (shows.isNotEmpty()) add(Row("TV Shows", shows))
            if (movies.isNotEmpty()) add(Row("Movies", movies))
        }
    }

    private fun focusFirstCard() {
        val firstRow = rows.findViewHolderForAdapterPosition(0) as? RowsAdapter.RowVH ?: return
        firstRow.list.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        if (!BuildConfig.DEMO_MODE) lifecycleScope.launch { repo.scan(); repo.enrichMissing() }
    }
}
