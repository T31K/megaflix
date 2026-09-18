package com.megaflix.tv.data

import com.megaflix.tv.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class TmdbMatch(
    val tmdbId: Long,
    val posterPath: String?,
    val backdropPath: String?,
    val overview: String?,
    val rating: Double?,
    val genres: String?,
)

data class TmdbCastMember(val name: String, val role: String?, val profilePath: String?)

/**
 * Minimal TMDB v3 client. No SDK deps: HttpURLConnection + org.json.
 * Pure parse functions are unit-tested; network wrappers are thin.
 */
object TmdbClient {

    private const val BASE = "https://api.themoviedb.org/3"

    fun posterUrl(path: String?) = path?.let { "https://image.tmdb.org/t/p/w342$it" }
    fun backdropUrl(path: String?) = path?.let { "https://image.tmdb.org/t/p/w1280$it" }
    fun profileUrl(path: String?) = path?.let { "https://image.tmdb.org/t/p/w185$it" }

    suspend fun search(title: String, year: Int?, isTv: Boolean): TmdbMatch? {
        val kind = if (isTv) "tv" else "movie"
        val yearParam = when {
            year == null -> ""
            isTv -> "&first_air_date_year=$year"
            else -> "&year=$year"
        }
        val q = URLEncoder.encode(title, "UTF-8")
        val url = "$BASE/search/$kind?api_key=${BuildConfig.TMDB_API_KEY}&query=$q$yearParam"
        return get(url)?.let(::parseSearch)
    }

    suspend fun credits(tmdbId: Long, isTv: Boolean): List<TmdbCastMember> {
        val kind = if (isTv) "tv" else "movie"
        val url = "$BASE/$kind/$tmdbId/credits?api_key=${BuildConfig.TMDB_API_KEY}"
        return get(url)?.let(::parseCredits) ?: emptyList()
    }

    fun parseSearch(json: String): TmdbMatch? {
        val results = JSONObject(json).optJSONArray("results") ?: return null
        if (results.length() == 0) return null
        val r = results.getJSONObject(0)
        val genreNames = r.optJSONArray("genre_ids")?.let { ids ->
            (0 until ids.length()).mapNotNull { GENRES[ids.getInt(it)] }
        }?.takeIf { it.isNotEmpty() }?.joinToString(" / ")
        return TmdbMatch(
            tmdbId = r.getLong("id"),
            posterPath = r.optString("poster_path").ifEmpty { null }
                ?.takeIf { it != "null" },
            backdropPath = r.optString("backdrop_path").ifEmpty { null }
                ?.takeIf { it != "null" },
            overview = r.optString("overview").ifEmpty { null },
            rating = r.optDouble("vote_average").takeIf { !it.isNaN() && it > 0.0 },
            genres = genreNames,
        )
    }

    fun parseCredits(json: String): List<TmdbCastMember> {
        val cast = JSONObject(json).optJSONArray("cast") ?: return emptyList()
        return (0 until minOf(cast.length(), 12)).map { i ->
            val c = cast.getJSONObject(i)
            TmdbCastMember(
                name = c.getString("name"),
                role = c.optString("character").ifEmpty { null },
                profilePath = c.optString("profile_path").ifEmpty { null }
                    ?.takeIf { it != "null" },
            )
        }
    }

    private suspend fun get(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            try {
                if (conn.responseCode == 200) conn.inputStream.bufferedReader().readText()
                else null
            } finally {
                conn.disconnect()
            }
        }.getOrNull() // offline / TMDB down → no enrichment, never crash
    }

    /** TMDB genre ids → names (movie + TV merged; duplicates share names). */
    private val GENRES = mapOf(
        28 to "Action", 12 to "Adventure", 16 to "Animation", 35 to "Comedy",
        80 to "Crime", 99 to "Documentary", 18 to "Drama", 10751 to "Family",
        14 to "Fantasy", 36 to "History", 27 to "Horror", 10402 to "Music",
        9648 to "Mystery", 10749 to "Romance", 878 to "Science Fiction",
        10770 to "TV Movie", 53 to "Thriller", 10752 to "War", 37 to "Western",
        10759 to "Action & Adventure", 10762 to "Kids", 10763 to "News",
        10764 to "Reality", 10765 to "Sci-Fi & Fantasy", 10766 to "Soap",
        10767 to "Talk", 10768 to "War & Politics",
    )
}
