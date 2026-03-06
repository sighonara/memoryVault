package org.sightech.memoryvault.youtube.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class YtDlpServiceTest {

    private val service = YtDlpService(ObjectMapper())
    private val sampleJson = this::class.java.classLoader.getResource("fixtures/sample-playlist.json")!!.readText()

    @Test
    fun `parsePlaylistJson parses all videos`() {
        val result = service.parsePlaylistJson(sampleJson)
        assertEquals(3, result.size)
    }

    @Test
    fun `parsePlaylistJson extracts video metadata`() {
        val result = service.parsePlaylistJson(sampleJson)
        val first = result[0]

        assertEquals("dQw4w9WgXcQ", first.videoId)
        assertEquals("Rick Astley - Never Gonna Give You Up", first.title)
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", first.url)
        assertEquals("Rick Astley", first.channel)
        assertEquals(212, first.durationSeconds)
    }

    @Test
    fun `parsePlaylistJson generates URL from ID when url is null`() {
        val json = """{"id": "abc123", "title": "Test"}"""
        val result = service.parsePlaylistJson(json)

        assertEquals("https://www.youtube.com/watch?v=abc123", result[0].url)
    }

    @Test
    fun `parsePlaylistJson skips blank lines`() {
        val jsonWithBlanks = sampleJson + "\n\n\n"
        val result = service.parsePlaylistJson(jsonWithBlanks)
        assertEquals(3, result.size)
    }
}
