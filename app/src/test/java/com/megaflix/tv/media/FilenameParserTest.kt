package com.megaflix.tv.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FilenameParserTest {

    @Test fun `tv episode with release junk`() {
        val p = FilenameParser.parse(
            "House.of.the.Dragon.S03E05.1080p.HEVC.x265-MeGusta[EZTVx.to].mkv"
        )
        assertEquals("House of the Dragon", p.title)
        assertEquals(3, p.season)
        assertEquals(5, p.episode)
        assertNull(p.year)
    }

    @Test fun `movie with year in parens`() {
        val p = FilenameParser.parse("Blade Runner 2049 (2017) 1080p BluRay x264.mp4")
        assertEquals("Blade Runner 2049", p.title)
        assertEquals(2017, p.year)
        assertNull(p.season)
    }

    @Test fun `movie with dots and bare year`() {
        val p = FilenameParser.parse("The.Matrix.1999.720p.WEB-DL.mp4")
        assertEquals("The Matrix", p.title)
        assertEquals(1999, p.year)
    }

    @Test fun `strips scene tags without year`() {
        val p = FilenameParser.parse("Some_Movie_Name.PROPER.REPACK.HDTV.x264-GROUP.mkv")
        assertEquals("Some Movie Name", p.title)
        assertNull(p.year)
    }

    @Test fun `lowercase sxxexx variant`() {
        val p = FilenameParser.parse("severance.s02e01.2160p.mp4")
        assertEquals("Severance", p.title)
        assertEquals(2, p.season)
        assertEquals(1, p.episode)
    }

    @Test fun `plain title no junk`() {
        val p = FilenameParser.parse("Amelie.mkv")
        assertEquals("Amelie", p.title)
        assertNull(p.year)
        assertNull(p.season)
    }

    @Test fun `number that is really the title`() {
        val p = FilenameParser.parse("1917.2019.1080p.BluRay.mp4")
        assertEquals("1917", p.title)
        assertEquals(2019, p.year)
    }
}
