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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.megaflix.tv.App
import com.megaflix.tv.BuildConfig
import com.megaflix.tv.R
import com.megaflix.tv.data.VideoEntity
import com.megaflix.tv.ui.compose.CardItem
import com.megaflix.tv.ui.compose.HomeRows
import com.megaflix.tv.ui.compose.toCardItem
import com.megaflix.tv.ui.compose.theme.MegaflixTheme
import com.megaflix.tv.update.UpdateChecker
import com.megaflix.tv.update.UpdateInfo
import com.megaflix.tv.update.UpdateInstaller
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class LibraryActivity : AppCompatActivity() {

    private val repo by lazy { (application as App).graph.repository }
    private lateinit var rowsCompose: ComposeView
    private lateinit var empty: TextView
    private lateinit var navBar: View
    private lateinit var navTabs: List<TextView>
    private var lastNavTab: TextView? = null

    // Compose-observable home content; updated as scan/enrichment emit.
    private var rowsState by mutableStateOf<List<Pair<String, List<CardItem>>>>(emptyList())
    private var requestedInitialFocus = false

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { rescanAndObserve() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_library)

        empty = findViewById(R.id.empty)
        setUpNavTabs()

        rowsCompose = findViewById(R.id.rows_compose)
        rowsCompose.setContent {
            MegaflixTheme {
                HomeRows(
                    rows = rowsState,
                    onClick = { id ->
                        startActivity(
                            Intent(this, DetailActivity::class.java)
                                .putExtra(DetailActivity.EXTRA_ID, id)
                        )
                    },
                )
            }
        }

        ensurePermissionThenScan()
        checkForUpdate()
    }

    /** Fetch version.json off Pages; if newer, offer a one-tap self-update. */
    private fun checkForUpdate() {
        lifecycleScope.launch {
            val info = UpdateChecker.check(installedVersionCode()) ?: return@launch
            androidx.appcompat.app.AlertDialog.Builder(this@LibraryActivity)
                .setTitle("Update available")
                .setMessage(
                    "Megaflix ${info.versionName} is ready to install." +
                        (info.notes?.let { "\n\n$it" } ?: "")
                )
                .setPositiveButton("Update") { _, _ -> downloadAndInstall(info) }
                .setNegativeButton("Later", null)
                .show()
        }
    }

    private fun downloadAndInstall(info: UpdateInfo) {
        android.widget.Toast
            .makeText(this, "Downloading update…", android.widget.Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val apk = UpdateChecker.download(this@LibraryActivity, info)
            if (apk != null) {
                UpdateInstaller.install(this@LibraryActivity, apk)
            } else {
                android.widget.Toast.makeText(
                    this@LibraryActivity, "Update download failed",
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    /** The currently-installed versionCode, read at runtime (no BuildConfig needed). */
    private fun installedVersionCode(): Int {
        val pkg = packageManager.getPackageInfo(packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) pkg.longVersionCode.toInt() else @Suppress("DEPRECATION") pkg.versionCode
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
        navTabs.forEach { tab ->
            tab.setOnFocusChangeListener { v, focused -> if (focused) lastNavTab = v as TextView }
        }
        findViewById<TextView>(R.id.tab_search).setOnClickListener {
            startActivity(Intent(this, SearchActivity::class.java))
        }
    }

    /**
     * DOWN from the nav bar hands focus into the Compose rows. UP out of the top
     * row back to the nav bar is handled by the framework's focus search (the
     * ComposeView sits directly below the nav bar in the layout).
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN &&
            event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN
        ) {
            val focused = currentFocus
            if (focused != null && isInNavBar(focused)) {
                rowsCompose.requestFocus()
                return true
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
            // Enrich concurrently: rows render now with placeholders, posters fill
            // in as TMDB responds (observeLibrary re-emits).
            launch { repo.enrichMissing() }
            repo.observeLibrary().collectLatest { list ->
                val built = buildRows(list)
                rowsState = built
                empty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                if (built.isNotEmpty() && !requestedInitialFocus) {
                    requestedInitialFocus = true
                    rowsCompose.post { rowsCompose.requestFocus() }
                }
            }
        }
    }

    /** Group the flat library into titled home rows of CardItems (skips empties). */
    private fun buildRows(all: List<VideoEntity>): List<Pair<String, List<CardItem>>> {
        if (all.isEmpty()) return emptyList()
        val continueWatching = all.filter { it.positionMs > 0 }
        val shows = all.filter { it.season != null }
        val movies = all.filter { it.season == null && it.year != null }
        return buildList {
            if (continueWatching.isNotEmpty()) add("Continue Watching" to continueWatching.map { it.toCardItem() })
            add("Recently Added" to all.map { it.toCardItem() })
            if (shows.isNotEmpty()) add("TV Shows" to shows.map { it.toCardItem() })
            if (movies.isNotEmpty()) add("Movies" to movies.map { it.toCardItem() })
        }
    }

    override fun onResume() {
        super.onResume()
        if (!BuildConfig.DEMO_MODE) lifecycleScope.launch { repo.scan(); repo.enrichMissing() }
    }
}
