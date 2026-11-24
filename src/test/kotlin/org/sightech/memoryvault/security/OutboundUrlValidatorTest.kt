package org.sightech.memoryvault.security

import org.junit.jupiter.api.Test
import java.net.InetAddress
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OutboundUrlValidatorTest {

    private val validator = OutboundUrlValidator(resolve = { host ->
        when (host) {
            "internal.example" -> listOf(InetAddress.getByName("192.168.1.1"))
            "mixed.example" -> listOf(InetAddress.getByName("93.184.216.34"), InetAddress.getByName("10.0.0.1"))
            "example.com" -> listOf(InetAddress.getByName("93.184.216.34"))
            else -> listOf(InetAddress.getByName(host))
        }
    })

    @Test
    fun `rejects blocked and non-http urls`() {
        listOf(
            "http://169.254.169.254/latest/meta-data/",
            "http://127.0.0.1:8085/",
            "http://10.0.0.5/",
            "http://100.64.0.1/",
            "http://0.1.2.3/",
            "http://[::1]/",
            "http://[fd00::1]/",
            "file:///etc/passwd",
            "ftp://x/",
            "http://internal.example/feed",
            "http://mixed.example/feed",
            "not a url"
        ).forEach { url ->
            assertFailsWith<UnsafeUrlException>(url) { validator.validate(url) }
        }
    }

    @Test
    fun `accepts public https url`() {
        assertEquals("example.com", validator.validate("https://example.com/feed.xml").host)
    }

    @Test
    fun `default constructor is usable`() {
        assertFailsWith<UnsafeUrlException> { OutboundUrlValidator().validate("http://127.0.0.1/") }
    }
}
