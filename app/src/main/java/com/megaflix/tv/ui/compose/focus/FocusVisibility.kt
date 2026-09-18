package com.megaflix.tv.ui.compose.focus

internal fun focusRevealDelta(top: Float, bottom: Float, viewportHeight: Float): Float = when {
    viewportHeight <= 0f -> 0f
    bottom - top > viewportHeight -> top
    top < 0f -> top
    bottom > viewportHeight -> bottom - viewportHeight
    else -> 0f
}
