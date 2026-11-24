package org.sightech.memoryvault.youtube.service

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tools.jackson.databind.ObjectMapper
import kotlin.test.assertEquals

class YoutubeUrlValidatorTest {

    @Test
    fun `accepts youtube urls and returns them unchanged`() {
        listOf(
            "https://www.youtube.com/playlist?list=PL1",
            "https://youtube.com/watch?v=x",
            "https://youtu.be/x",
            "https://m.youtube.com/watch?v=x"
        ).forEach { assertEquals(it, YoutubeUrlValidator.requireYoutubeUrl(it)) }
    }

    @Test
    fun `rejects non youtube or non https urls`() {
        listOf(
            "--batch-file=/etc/passwd",
            "http://www.youtube.com/playlist?list=PL1",
            "https://youtube.com.evil.com/x",
            "https://evil.com/?u=youtube.com",
            "https://notyoutube.com/x",
            "file:///x",
            "",
            "not a url"
        ).forEach { url ->
            val e = assertThrows<IllegalArgumentException>(url) { YoutubeUrlValidator.requireYoutubeUrl(url) }
            assertEquals("Not a YouTube URL", e.message)
        }
    }

    @Test
    fun `metadata command ends with double dash then url`() {
        val service = YtDlpService(ObjectMapper(), downloadTimeoutMinutes = 30)
        val cmd = service.metadataCommand("https://youtu.be/x")
        assertEquals(listOf("--", "https://youtu.be/x"), cmd.takeLast(2))
    }

    @Test
    fun `download command ends with double dash then url`() {
        val service = YtDlpService(ObjectMapper(), downloadTimeoutMinutes = 30)
        val cmd = service.downloadCommand("https://youtu.be/x", "/tmp/out")
        assertEquals(listOf("--", "https://youtu.be/x"), cmd.takeLast(2))
        assertEquals(listOf("-o", "/tmp/out"), cmd.subList(cmd.indexOf("-o"), cmd.indexOf("-o") + 2))
    }

    @Test
    fun `fetch and download reject non youtube urls before starting a process`() {
        val service = YtDlpService(ObjectMapper(), downloadTimeoutMinutes = 30)
        assertThrows<IllegalArgumentException> { service.fetchPlaylistMetadata("--batch-file=/etc/passwd") }
        assertThrows<IllegalArgumentException> { service.downloadVideo("https://evil.com/x", "/tmp/out") }
    }
}
