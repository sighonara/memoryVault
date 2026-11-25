package org.sightech.memoryvault.youtube.service

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.sightech.memoryvault.security.InvalidYoutubeUrlException
import kotlin.test.assertEquals

class YoutubeUrlValidatorTest {

    @Test
    fun `accepts youtube urls and returns them unchanged`() {
        listOf(
            "https://www.youtube.com/playlist?list=PL1",
            "https://youtube.com/watch?v=x",
            "https://youtu.be/x",
            "https://m.youtube.com/watch?v=x",
            "HTTPS://WWW.YOUTUBE.COM/watch?v=x"
        ).forEach { assertEquals(it, YoutubeUrlValidator.requireYoutubeUrl(it)) }
    }

    @Test
    fun `upgrades scheme-less and http youtube urls to https`() {
        assertEquals("https://youtube.com/playlist?list=X", YoutubeUrlValidator.requireYoutubeUrl("youtube.com/playlist?list=X"))
        assertEquals(
            "https://www.youtube.com/playlist?list=X",
            YoutubeUrlValidator.requireYoutubeUrl("http://www.youtube.com/playlist?list=X")
        )
        assertEquals("https://youtu.be/x", YoutubeUrlValidator.requireYoutubeUrl("youtu.be/x"))
    }

    @Test
    fun `rejects scheme-less and http urls on other hosts`() {
        listOf("evil.com/playlist?list=X", "http://evil.com/playlist?list=X", "http://youtube.com.evil.com/x").forEach { url ->
            assertThrows<InvalidYoutubeUrlException>(url) { YoutubeUrlValidator.requireYoutubeUrl(url) }
        }
    }

    @Test
    fun `rejects non youtube or non https urls`() {
        listOf(
            "--batch-file=/etc/passwd",
            "https://youtube.com.evil.com/x",
            "https://evil.com/?u=youtube.com",
            "https://notyoutube.com/x",
            "file:///x",
            "",
            "not a url",
            "https://evil.com#youtube.com",
            "https://youtube.com@evil.com/",
            "https://evil.com\\@youtube.com/",
            "javascript:alert(1)",
            " https://www.youtube.com/watch?v=x",
            "\nhttps://www.youtube.com/watch?v=x",
            "https://yоutube.com/x",
            "https://youtube.com./x"
        ).forEach { url ->
            val e = assertThrows<IllegalArgumentException>(url) { YoutubeUrlValidator.requireYoutubeUrl(url) }
            assertEquals("Not a YouTube URL", e.message)
        }
    }

}
