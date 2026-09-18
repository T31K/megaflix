package com.megaflix.tv.ui.compose.components

// Adapted from ARVIO (https://github.com/ProdigyV21/ARVIO), Apache-2.0 —
// reimplemented slim on ARVIO's ArvioFocusableSurface (its 799-line MediaCard
// is fused to Hilt/DataStore/data models, so we keep only the focus primitive).

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.megaflix.tv.ui.compose.CardItem
import com.megaflix.tv.ui.compose.skin.ArvioFocusableSurface
import com.megaflix.tv.ui.compose.skin.ArvioSkin
import com.megaflix.tv.ui.compose.skin.rememberArvioCardShape
import com.megaflix.tv.ui.compose.theme.ArflixTypography

/** A poster tile (2:3) with ARVIO focus treatment, title fallback, and a resume bar. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MediaCard(
    item: CardItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
) {
    val shape = rememberArvioCardShape(10.dp)
    ArvioFocusableSurface(
        // ArvioFocusableSurface lays content out with matchParentSize, so the
        // surface itself must carry an explicit size (2:3 poster).
        modifier = modifier.width(132.dp).height(198.dp),
        shape = shape,
        backgroundColor = ArvioSkin.colors.surface,
        outlineColor = ArvioSkin.colors.focusOutline,
        onClick = onClick,
        onFocusChanged = { if (it) onFocused() },
    ) { _ ->
        Box(
            Modifier
                .fillMaxSize()
                .clip(shape),
            contentAlignment = Alignment.Center,
        ) {
            if (item.posterUrl != null) {
                AsyncImage(
                    model = item.posterUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                // No art yet — show the title over the surface colour.
                Text(
                    text = item.year?.let { "${item.title} ($it)" } ?: item.title,
                    style = ArflixTypography.cardTitle,
                    color = ArvioSkin.colors.textPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(8.dp),
                )
            }

            // Resume progress bar pinned to the bottom edge.
            val progress = item.progressFraction
            if (progress != null && progress > 0f) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(Color.Black.copy(alpha = 0.5f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress)
                            .height(4.dp)
                            .background(ArvioSkin.colors.accent),
                    )
                }
            }
        }
    }
}
