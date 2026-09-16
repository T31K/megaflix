package com.megaflix.tv.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.megaflix.tv.App
import com.megaflix.tv.BuildConfig
import com.megaflix.tv.R
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class LibraryActivity : AppCompatActivity() {

    private val repo by lazy { (application as App).graph.repository }
    private lateinit var adapter: PosterAdapter
    private lateinit var empty: TextView

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { rescanAndObserve() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_library)

        empty = findViewById(R.id.empty)
        adapter = PosterAdapter { video ->
            startActivity(
                Intent(this, PlayerActivity::class.java)
                    .putExtra(PlayerActivity.EXTRA_URI, video.uri)
            )
        }
        findViewById<RecyclerView>(R.id.grid).apply {
            layoutManager = GridLayoutManager(this@LibraryActivity, 6)
            adapter = this@LibraryActivity.adapter
            setHasFixedSize(true)
        }
        ensurePermissionThenScan()
    }

    private fun ensurePermissionThenScan() {
        // Demo mode plays a bundled resource — no storage permission needed.
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
            repo.observeLibrary().collectLatest { list ->
                adapter.submitList(list)
                empty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-scan on return so newly pushed files and updated progress show up.
        lifecycleScope.launch { repo.scan() }
    }
}
