package com.megaflix.tv.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.megaflix.tv.App
import com.megaflix.tv.R
import com.megaflix.tv.data.VideoEntity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Search screen: a focusable text box over a poster grid. Filters the library
 * by title (case-insensitive substring) as the user types. Results open the
 * same DetailActivity as the home rows.
 */
class SearchActivity : AppCompatActivity() {

    private val repo by lazy { (application as App).graph.repository }
    private lateinit var adapter: PosterAdapter
    private lateinit var results: RecyclerView
    private lateinit var empty: TextView
    private var library: List<VideoEntity> = emptyList()
    private var query: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        empty = findViewById(R.id.search_empty)
        adapter = PosterAdapter { video ->
            startActivity(
                Intent(this, DetailActivity::class.java)
                    .putExtra(DetailActivity.EXTRA_ID, video.id)
            )
        }
        results = findViewById(R.id.search_results)
        results.layoutManager = GridLayoutManager(this, 6)
        results.adapter = adapter

        findViewById<EditText>(R.id.search_input).addTextChangedListener { text ->
            query = text?.toString().orEmpty().trim()
            applyFilter()
        }

        lifecycleScope.launch {
            repo.observeLibrary().collectLatest { list ->
                library = list
                applyFilter()
            }
        }
    }

    private fun applyFilter() {
        val matches = if (query.isEmpty()) emptyList()
        else library.filter { it.title.contains(query, ignoreCase = true) }
        adapter.submitList(matches)
        empty.visibility = if (matches.isEmpty()) View.VISIBLE else View.GONE
        empty.text = getString(
            if (query.isEmpty()) R.string.search_prompt else R.string.search_none
        )
    }
}
