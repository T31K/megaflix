package com.megaflix.tv.ui

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.megaflix.tv.R
import com.megaflix.tv.data.VideoEntity

/**
 * Vertical list of home rows. Each row hosts its own horizontal RecyclerView of
 * posters, all sharing one RecycledViewPool so scrolling stays allocation-free.
 */
class RowsAdapter(
    private val onClick: (VideoEntity) -> Unit,
) : RecyclerView.Adapter<RowsAdapter.RowVH>() {

    private val sharedPool = RecyclerView.RecycledViewPool()
    private var rows: List<Row> = emptyList()

    fun submit(newRows: List<Row>) {
        rows = newRows
        notifyDataSetChanged()
    }

    class RowVH(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.row_title)
        val list: RecyclerView = view.findViewById(R.id.row_list)
    }

    // Lint can't see the layout manager is HORIZONTAL (set here, not in XML), so it
    // wrongly flags wrap_content height as the scrolling axis. The row height is
    // fixed and the list scrolls horizontally, so setHasFixedSize is valid.
    @SuppressLint("InvalidSetHasFixedSize")
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowVH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_row, parent, false)
        val vh = RowVH(v)
        vh.list.layoutManager =
            LinearLayoutManager(parent.context, LinearLayoutManager.HORIZONTAL, false)
        vh.list.setRecycledViewPool(sharedPool)
        vh.list.setHasFixedSize(true)
        return vh
    }

    override fun onBindViewHolder(holder: RowVH, position: Int) {
        val row = rows[position]
        holder.title.text = row.title
        val adapter = holder.list.adapter as? PosterAdapter ?: PosterAdapter(onClick).also {
            holder.list.adapter = it
        }
        adapter.submitList(row.items)
    }

    override fun getItemCount(): Int = rows.size
}
