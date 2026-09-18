package com.megaflix.tv.ui.compose.theme

import androidx.compose.runtime.Composable

/**
 * Megaflix's Compose theme entry point.
 *
 * Adapted from ARVIO (https://github.com/ProdigyV21/ARVIO), Apache-2.0 — the
 * "Arctic Fuse 2" design system (light-gray #EDEDED on near-black, white focus).
 * We keep ARVIO's palette intact because its look is the whole reason for the
 * transplant; this is a thin alias over [ArvioTvTheme].
 */
@Composable
fun MegaflixTheme(content: @Composable () -> Unit) {
    ArvioTvTheme(oledBlackBackground = false, accentColorName = null, content = content)
}
