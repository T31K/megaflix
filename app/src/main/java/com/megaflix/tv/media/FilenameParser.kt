package com.megaflix.tv.media

data class ParsedName(
    val title: String,
    val year: Int?,
    val season: Int?,
    val episode: Int?,
)

/**
 * Turns "House.of.the.Dragon.S03E05.1080p.HEVC.x265-MeGusta[EZTVx.to].mkv"
 * into ParsedName("House of the Dragon", null, 3, 5).
 *
 * Strategy:
 *  - A bracketed year like (2017) is treated as THE year and a hard title
 *    boundary, so an in-title number (Blade Runner 2049) is not mistaken for it.
 *  - Otherwise strip bracket junk, normalise separators to spaces, and cut the
 *    title at the first scene tag, SxxExx token, or bare 4-digit year.
 */
object FilenameParser {

    private val SXXEXX = Regex("""[sS](\d{1,2})[eE](\d{1,3})""")
    private val YEAR = Regex("""\b(19\d{2}|20\d{2})\b""")
    private val PAREN_YEAR = Regex("""[\[(](19\d{2}|20\d{2})[\])]""")
    private val BRACKETS = Regex("""[\[(][^\])]*[\])]""")

    // Words kept lowercase inside a title (unless first word).
    private val SMALL = setOf(
        "of", "the", "a", "an", "and", "or", "but", "to", "in", "on",
        "at", "for", "with", "from", "by", "as", "nor",
    )

    // Common release/scene tags that mark the end of a title.
    private val TAGS = setOf(
        "1080p", "720p", "480p", "2160p", "4k", "uhd",
        "x264", "x265", "h264", "h265", "hevc", "avc", "xvid", "divx",
        "bluray", "brrip", "bdrip", "webrip", "web-dl", "webdl", "web",
        "hdtv", "hdrip", "dvdrip", "dvdscr", "cam", "ts", "hdr", "hdr10",
        "aac", "ac3", "dts", "dd5", "atmos", "truehd", "10bit",
        "proper", "repack", "extended", "remastered", "internal", "limited",
    )

    fun parse(filename: String): ParsedName {
        val noExt = filename.substringBeforeLast('.').ifBlank { filename }

        val sxx = SXXEXX.find(noExt)
        val season = sxx?.groupValues?.get(1)?.toIntOrNull()
        val episode = sxx?.groupValues?.get(2)?.toIntOrNull()

        // A bracketed year is a strong "this is THE year" signal; everything
        // before it is the title, so in-title numbers survive.
        val parenYear = PAREN_YEAR.find(noExt)
        var year: Int? = parenYear?.groupValues?.get(1)?.toInt()
        val titleRegion = if (parenYear != null) noExt.substring(0, parenYear.range.first) else noExt

        val normalized = BRACKETS.replace(titleRegion, " ")
            .replace(Regex("""[._]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

        val tokens = normalized.split(' ').filter { it.isNotBlank() }
        var cutIndex = tokens.size

        for ((i, raw) in tokens.withIndex()) {
            if (i == 0) continue // never cut before the first token; a title can start with a number (1917)
            val t = raw.lowercase().trimEnd('-')
            if (SXXEXX.matches(raw)) { cutIndex = minOf(cutIndex, i); continue }
            if (t.trimStart('-') in TAGS || t.substringBefore('-') in TAGS) {
                cutIndex = minOf(cutIndex, i); continue
            }
            // Only treat a bare token as the year when no bracketed year was found.
            if (parenYear == null && YEAR.matchEntire(raw) != null) {
                if (year == null) year = raw.toInt()
                cutIndex = minOf(cutIndex, i)
            }
        }

        val title = tokens.take(cutIndex).joinToString(" ")
            .replace(Regex("""[-–]\s*[A-Za-z0-9]+$"""), "") // trailing "-GROUP"
            .trim()
            .ifBlank { tokens.firstOrNull().orEmpty() }

        return ParsedName(titleCase(title), year, season, episode)
    }

    private fun titleCase(s: String): String {
        val words = s.split(' ').filter { it.isNotBlank() }
        return words.mapIndexed { i, w ->
            when {
                w.any { it.isDigit() } -> w                       // keep 1917, 2049 as-is
                i != 0 && w.lowercase() in SMALL -> w.lowercase() // "of", "the" stay lowercase
                else -> w.replaceFirstChar { it.uppercase() }
            }
        }.joinToString(" ")
    }
}
