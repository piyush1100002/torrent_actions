package com.torrentactions.app

import com.torrentactions.app.util.MagnetUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MagnetUtilsTest {

    @Test
    fun testExtractDisplayName() {
        val magnet = "magnet:?xt=urn:btih:08ada5a7a6183aae1e09d831df6748d566095a10&dn=Inception.2010.1080p.BluRay.x264"
        val title = MagnetUtils.extractDisplayName(magnet)
        assertEquals("Inception.2010.1080p.BluRay.x264", title)
    }

    @Test
    fun testExtractInfoHash() {
        val magnet = "magnet:?xt=urn:btih:08ada5a7a6183aae1e09d831df6748d566095a10&dn=Inception"
        val hash = MagnetUtils.extractInfoHash(magnet)
        assertEquals("08ada5a7a6183aae1e09d831df6748d566095a10", hash)
    }

    @Test
    fun testExtractMagnetFromText() {
        val textWithMagnet = "Check out this movie: magnet:?xt=urn:btih:08ada5a7a6183aae1e09d831df6748d566095a10&dn=Inception please download"
        val extracted = MagnetUtils.extractMagnetFromText(textWithMagnet)
        assertNotNull(extracted)
        assertTrue(extracted!!.startsWith("magnet:?xt=urn:btih:"))
    }

    @Test
    fun testCleanImdbId() {
        assertEquals("tt1375666", MagnetUtils.cleanImdbId("tt1375666"))
        assertEquals("tt1375666", MagnetUtils.cleanImdbId("https://www.imdb.com/title/tt1375666/"))
        assertEquals("tt0816692", MagnetUtils.cleanImdbId("TT0816692"))
    }
}
