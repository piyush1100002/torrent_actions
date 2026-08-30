package com.torrentactions.app

import com.torrentactions.app.data.api.GitHubApiClient
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class GitHubLogDecoderTest {

    @Test
    fun decodeLogPayload_handlesZipArchive() {
        val payload = "line 1\nline 2\n"
        val zipBytes = zipText(payload)

        val decoded = GitHubApiClient.decodeLogPayload(zipBytes)

        assertTrue(decoded.contains("line 1"))
        assertTrue(decoded.contains("line 2"))
    }

    private fun zipText(text: String): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            zip.putNextEntry(ZipEntry("logs.txt"))
            zip.write(text.toByteArray())
            zip.closeEntry()
        }
        return baos.toByteArray()
    }
}
