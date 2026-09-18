package com.megaflix.tv.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.megaflix.tv.R
import com.megaflix.tv.data.TmdbCastMember
import com.megaflix.tv.data.TmdbClient

class CastAdapter : RecyclerView.Adapter<CastAdapter.VH>() {

    private var items: List<TmdbCastMember> = emptyList()

    fun submit(list: List<TmdbCastMember>) {
        items = list
        notifyDataSetChanged() // small fixed list; DiffUtil is overkill here
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val photo: ImageView = view.findViewById(R.id.cast_photo)
        val name: TextView = view.findViewById(R.id.cast_name)
        val role: TextView = view.findViewById(R.id.cast_role)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        LayoutInflater.from(parent.context).inflate(R.layout.item_cast, parent, false)
    )

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val c = items[position]
        holder.name.text = c.name
        holder.role.text = c.role ?: ""
        val url = TmdbClient.profileUrl(c.profilePath)
        if (url != null) holder.photo.load(url) { crossfade(true) }
        else holder.photo.setImageDrawable(null)
    }
}
