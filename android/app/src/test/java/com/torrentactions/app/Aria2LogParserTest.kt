package com.torrentactions.app

import com.torrentactions.app.data.parser.Aria2LogParser
import com.torrentactions.app.data.parser.WorkerStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class Aria2LogParserTest {

    @Test
    fun testParseDownloadingLog() {
        val sampleLog = """
            Starting torrent download for: tt1375666
            IMDb ID: tt1375666
            Source: magnet:?xt=urn:btih:08ada5a7a6183aae1e09d831df6748d566095a10&dn=Inception.2010...
            [#178229 1.2GiB/4.5GiB(26%) CN:18 DL:24.5MiB ETA:2m15s]
        """.trimIndent()

        val progress = Aria2LogParser.parseLogs(sampleLog, "in_progress", null)

        assertEquals(WorkerStage.DOWNLOADING, progress.stage)
        assertEquals(26, progress.progressPercent)
        assertEquals("1.2GiB", progress.downloadedSize)
        assertEquals("4.5GiB", progress.totalSize)
        assertEquals("24.5MiB/s", progress.downloadSpeed)
        assertEquals("2m15s", progress.eta)
        assertEquals(18, progress.peerCount)
        assertEquals("tt1375666", progress.imdbId)
    }

    @Test
    fun testParseDownloadingLogWithoutEta() {
        val sampleLog = """
            Starting torrent download for: tt1375666
            IMDb ID: tt1375666
            [#178229 1.2GiB/4.5GiB(26%) CN:18 DL:24.5MiB]
        """.trimIndent()

        val progress = Aria2LogParser.parseLogs(sampleLog, "in_progress", null)

        assertEquals(WorkerStage.DOWNLOADING, progress.stage)
        assertEquals(26, progress.progressPercent)
        assertEquals("1.2GiB", progress.downloadedSize)
        assertEquals("4.5GiB", progress.totalSize)
        assertEquals("24.5MiB/s", progress.downloadSpeed)
        assertEquals("--", progress.eta)
        assertEquals(18, progress.peerCount)
    }

    @Test
    fun testParseQbittorrentProgressLine() {
        val sampleLog = """
            Starting qBittorrent-nox on 127.0.0.1:8080.
            Cloudflare tunnel URL: https://prints-printed-mil-handbook.trycloudflare.com
            qBittorrent WebUI Password: session_secret_pass
            qBittorrent: 21.8% | 440.7 MiB / 1.99 GiB | 3.6 MiB/s | ETA 10m 10s | seeds 2, peers 1 | downloading
        """.trimIndent()

        val progress = Aria2LogParser.parseLogs(sampleLog, "in_progress", null)

        assertEquals(WorkerStage.DOWNLOADING, progress.stage)
        assertEquals(21, progress.progressPercent)
        assertEquals("440.7 MiB", progress.downloadedSize)
        assertEquals("1.99 GiB", progress.totalSize)
        assertEquals("3.6 MiB/s", progress.downloadSpeed)
        assertEquals("10m 10s", progress.eta)
        assertEquals(3, progress.peerCount)
        assertEquals("https://prints-printed-mil-handbook.trycloudflare.com", progress.tunnelUrl)
        assertEquals("session_secret_pass", progress.qbtPassword)
    }

    @Test
    fun testParseTransmissionProgressWithSpacedEta() {
        val sampleLog = """
            Starting torrent download for: tt1375666
            [#9f8eff 24.5MiB/50.0MiB(49%) CN:5 DL:2.4MiB/s ETA:1m 05s]
        """.trimIndent()

        val progress = Aria2LogParser.parseLogs(sampleLog, "in_progress", null)

        assertEquals(WorkerStage.DOWNLOADING, progress.stage)
        assertEquals(49, progress.progressPercent)
        assertEquals("24.5MiB", progress.downloadedSize)
        assertEquals("50.0MiB", progress.totalSize)
        assertEquals("2.4MiB/s", progress.downloadSpeed)
        assertEquals("1m 05s", progress.eta)
        assertEquals(5, progress.peerCount)
    }

    @Test
    fun testParseUploadToHfStage() {
        val sampleLog = """
            Download complete
            Files downloaded: 3
            ✅ Download finished successfully
            Uploading files to Hugging Face
            Starting upload of Inception to dataset...
        """.trimIndent()

        val progress = Aria2LogParser.parseLogs(sampleLog, "in_progress", null)

        assertEquals(WorkerStage.UPLOADING_HF, progress.stage)
        assertEquals(100, progress.progressPercent)
    }

    @Test
    fun testParseCompletedStage() {
        val sampleLog = """
            Uploading files to Hugging Face
            ✅ Upload complete
        """.trimIndent()

        val progress = Aria2LogParser.parseLogs(sampleLog, "completed", "success")

        assertEquals(WorkerStage.COMPLETED, progress.stage)
        assertEquals(100, progress.progressPercent)
    }

    @Test
    fun testStepSummaryInitializationDoesNotShowHfPush() {
        val sampleLog = """
            [Live step status — full logs available after job completes]

            ✅  Set up job
            ✅  Checkout repository
            ✅  Show user input
            ⏳  Install torrent download tools
            ⬜  Prepare download folder
            ⬜  Download .torrent file if the input is not a magnet link
            ⬜  Torrent download with qBittorrent
            ⬜  Install Hugging Face upload dependencies
            ⬜  Push downloaded media to Hugging Face dataset
        """.trimIndent()

        val progress = Aria2LogParser.parseLogs(sampleLog, "in_progress", null)

        assertEquals(WorkerStage.SETTING_UP, progress.stage)
        assertEquals(0, progress.progressPercent)
    }

    @Test
    fun testParseCancelledStage() {
        val sampleLog = """
            Download failed
        """.trimIndent()

        val progress = Aria2LogParser.parseLogs(sampleLog, "completed", "cancelled")

        assertEquals(WorkerStage.CANCELLED, progress.stage)
    }
}
