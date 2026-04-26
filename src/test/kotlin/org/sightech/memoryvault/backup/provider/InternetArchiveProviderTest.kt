package org.sightech.memoryvault.backup.provider

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class InternetArchiveProviderTest {

    @Test
    fun `itemIdentifier formats youtube video id correctly`() {
        val provider = InternetArchiveProvider("test-access", "test-secret")
        assertEquals("yt-dQw4w9WgXcQ", provider.itemIdentifier("dQw4w9WgXcQ"))
    }

    @Test
    fun `buildMetadataHeaders includes required fields`() {
        val provider = InternetArchiveProvider("test-access", "test-secret")
        val metadata = VideoBackupMetadata(
            youtubeVideoId = "dQw4w9WgXcQ",
            title = "Test Video",
            description = "A description",
            youtubeUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        )
        val headers = provider.buildMetadataHeaders(metadata)

        assertEquals("movies", headers["x-archive-meta-mediatype"])
        assertEquals("Test Video", headers["x-archive-meta-title"])
        assertEquals("dQw4w9WgXcQ", headers["x-archive-meta-youtube-video-id"])
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", headers["x-archive-meta-youtube-url"])
        assertEquals("memoryvault", headers["x-archive-meta-archived-by"])
        assertNotNull(headers["x-archive-meta-description"])
    }

    @Test
    fun `buildMetadataHeaders handles null title and description`() {
        val provider = InternetArchiveProvider("test-access", "test-secret")
        val metadata = VideoBackupMetadata(
            youtubeVideoId = "abc123",
            title = null,
            description = null,
            youtubeUrl = "https://www.youtube.com/watch?v=abc123"
        )
        val headers = provider.buildMetadataHeaders(metadata)

        assertNull(headers["x-archive-meta-title"])
        assertNull(headers["x-archive-meta-description"])
        assertEquals("abc123", headers["x-archive-meta-youtube-video-id"])
    }

    @Test
    fun `externalUrl formats correctly`() {
        val provider = InternetArchiveProvider("test-access", "test-secret")
        assertEquals("https://archive.org/details/yt-abc123", provider.externalUrl("abc123"))
    }
}
