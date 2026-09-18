package com.megaflix.tv.ui.compose

// Adapted from ARVIO (https://github.com/ProdigyV21/ARVIO), Apache-2.0.

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.megaflix.tv.ui.compose.components.MediaCard
import com.megaflix.tv.ui.compose.skin.ArvioSkin
import com.megaflix.tv.ui.compose.theme.ArflixTypography

/** Vertical list of titled poster rows — the Megaflix home surface. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeRows(
    rows: List<Pair<String, List<CardItem>>>,
    onClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        itemsIndexed(rows) { _, (title, items) ->
            Text(
                text = title,
                style = ArflixTypography.sectionTitle,
                color = ArvioSkin.colors.textPrimary,
                modifier = Modifier.padding(start = 48.dp, bottom = 10.dp),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(items, key = { it.id }) { card ->
                    MediaCard(item = card, onClick = { onClick(card.id) })
                }
            }
        }
    }
}
