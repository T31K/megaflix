package com.megaflix.tv.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.megaflix.tv.R
import com.megaflix.tv.data.VideoEntity

class PosterAdapter(
    private val onClick: (VideoEntity) -> Unit,
) : ListAdapter<VideoEntity, PosterAdapter.VH>(DIFF) {

    init { setHasStableIds(true) }
    override fun getItemId(position: Int): Long = getItem(position).id

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val poster: ImageView = view.findViewById(R.id.poster)
        val title: TextView = view.findViewById(R.id.title)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_poster, parent, false)
        // Round the corners by clipping to the (rounded) background outline.
        v.clipToOutline = true
        v.outlineProvider = ViewOutlineProvider.BACKGROUND
        val vh = VH(v)
        // Apple-TV-style focus: lift + soft shadow + gentle scale. Set once at
        // create time (no per-bind allocation), animated via a hardware layer.
        v.setOnFocusChangeListener { view, focused ->
            val s = if (focused) 1.10f else 1f
            view.animate().scaleX(s).scaleY(s)
                .translationZ(if (focused) 16f else 0f)
                .setDuration(160).start()
            if (focused) view.bringToFront()
        }
        return vh
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        val posterRes = TEST_POSTERS[item.title]
        if (posterRes != null) {
            holder.poster.setImageResource(posterRes)
            holder.title.visibility = View.GONE
        } else {
            holder.poster.setImageDrawable(null) // gradient placeholder shows through
            holder.title.visibility = View.VISIBLE
            holder.title.text = when {
                item.season != null && item.episode != null ->
                    "${item.title} S${item.season}E${item.episode}"
                item.year != null -> "${item.title} (${item.year})"
                else -> item.title
            }
        }
        holder.itemView.setOnClickListener { onClick(item) }
    }

    companion object {
        // TEST ONLY — hardcoded poster art keyed by parsed title. Phase 2 replaces
        // this with real TMDB poster paths stored per-video in Room.
        private val TEST_POSTERS = mapOf(
            "House of the Dragon" to R.drawable.poster_hotd,
            "Space Jam" to R.drawable.poster_space_jam,
        )

        private val DIFF = object : DiffUtil.ItemCallback<VideoEntity>() {
            override fun areItemsTheSame(a: VideoEntity, b: VideoEntity) = a.id == b.id
            override fun areContentsTheSame(a: VideoEntity, b: VideoEntity) = a == b
        }
    }
}
