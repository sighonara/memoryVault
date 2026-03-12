package org.sightech.memoryvault.bookmark.service

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class UrlNormalizationTest {

    @Test
    fun `normalizes scheme to lowercase`() {
        assertEquals("https://example.com/path", IngestService.normalizeUrl("HTTPS://Example.com/path"))
    }

    @Test
    fun `strips trailing slash`() {
        assertEquals("https://example.com", IngestService.normalizeUrl("https://example.com/"))
    }

    @Test
    fun `strips www prefix`() {
        assertEquals("https://example.com", IngestService.normalizeUrl("https://www.example.com"))
    }

    @Test
    fun `sorts query parameters`() {
        assertEquals("https://example.com?a=1&b=2", IngestService.normalizeUrl("https://example.com?b=2&a=1"))
    }

    @Test
    fun `preserves path case`() {
        assertEquals("https://example.com/MyPage", IngestService.normalizeUrl("https://example.com/MyPage"))
    }
}
