package org.sightech.memoryvault.youtube.service

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
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
    fun `rejects non youtube or non https urls`() {
        listOf(
            "--batch-file=/etc/passwd",
            "http://www.youtube.com/playlist?list=PL1",
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
