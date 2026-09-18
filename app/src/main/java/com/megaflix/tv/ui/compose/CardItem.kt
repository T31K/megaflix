package com.megaflix.tv.ui.compose

import com.megaflix.tv.data.TmdbClient
import com.megaflix.tv.data.VideoEntity

/**
 * The only currency between Megaflix's data layer and the ARVIO-derived UI.
 * Components never see [VideoEntity] or any ARVIO model — just this.
 */
data class CardItem(
    val id: Long,
    val title: String,
    val posterUrl: String?,
    val year: Int?,
    val progressFraction: Float?, // resume bar; null when unwatched
)

fun VideoEntity.toCardItem() = CardItem(
    id = id,
    title = title,
    posterUrl = TmdbClient.posterUrl(posterPath),
    year = year,
    progressFraction = if (positionMs > 0 && durationMs > 0)
        (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else null,
)
