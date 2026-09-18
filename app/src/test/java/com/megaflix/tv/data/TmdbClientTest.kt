package com.megaflix.tv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TmdbClientTest {

    @Test
    fun `parseSearch picks first result and maps fields`() {
        val json = """
        {"results":[{"id":603,"poster_path":"/p.jpg","backdrop_path":"/b.jpg",
        "overview":"A hacker learns the truth.","vote_average":8.2,"genre_ids":[28,878]}]}
        """.trimIndent()
        val m = TmdbClient.parseSearch(json)!!
        assertEquals(603L, m.tmdbId)
        assertEquals("/p.jpg", m.posterPath)
        assertEquals("/b.jpg", m.backdropPath)
        assertEquals("A hacker learns the truth.", m.overview)
        assertEquals(8.2, m.rating!!, 0.001)
        assertEquals("Action / Science Fiction", m.genres)
    }

    @Test
    fun `parseSearch returns null on empty results`() {
        assertNull(TmdbClient.parseSearch("""{"results":[]}"""))
    }

    @Test
    fun `parseCredits maps top cast`() {
        val json = """
        {"cast":[{"name":"Keanu Reeves","character":"Neo","profile_path":"/k.jpg"},
                 {"name":"Carrie-Anne Moss","character":"Trinity","profile_path":null}]}
        """.trimIndent()
        val cast = TmdbClient.parseCredits(json)
        assertEquals(2, cast.size)
        assertEquals("Keanu Reeves", cast[0].name)
        assertEquals("Neo", cast[0].role)
        assertEquals("/k.jpg", cast[0].profilePath)
        assertNull(cast[1].profilePath)
    }
}
