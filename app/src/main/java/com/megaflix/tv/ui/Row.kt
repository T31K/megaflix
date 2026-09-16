package com.megaflix.tv.ui

import com.megaflix.tv.data.VideoEntity

/** One horizontal row on the home screen: a title and its titles. */
data class Row(val title: String, val items: List<VideoEntity>)
