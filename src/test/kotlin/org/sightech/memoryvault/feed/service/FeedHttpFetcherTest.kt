package org.sightech.memoryvault.feed.service

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.security.FeedTooLargeException
import org.sightech.memoryvault.security.OutboundUrlValidator
import org.sightech.memoryvault.security.UnsafeUrlException
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FeedHttpFetcherTest {

    private lateinit var server: HttpServer
    private var base = ""

    // Loopback 127.0.0.1 is allowed for the test server; 127.0.0.2 stands in for a blocked address.
    private val validator = OutboundUrlValidator(
        resolve = { listOf(InetAddress.getByName(it)) },
        isBlocked = { it.hostAddress == "127.0.0.2" }
    )
    private val fetcher = FeedHttpFetcher(validator)

    @BeforeEach
    fun start() {
        server = HttpServer.create(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0)
        base = "http://127.0.0.1:${server.address.port}"
        server.createContext("/ok") { ex ->
            val body = "<rss>ok</rss>".toByteArray()
            ex.sendResponseHeaders(200, body.size.toLong())
            ex.responseBody.use { it.write(body) }
        }
        server.createContext("/redirect-ok") { ex ->
            ex.responseHeaders.add("Location", "/ok")
            ex.sendResponseHeaders(302, -1)
            ex.close()
        }
        server.createContext("/redirect-blocked") { ex ->
            ex.responseHeaders.add("Location", "http://127.0.0.2:1/secret")
            ex.sendResponseHeaders(302, -1)
            ex.close()
        }
        server.createContext("/loop") { ex ->
            ex.responseHeaders.add("Location", "/loop")
            ex.sendResponseHeaders(302, -1)
            ex.close()
        }
        server.createContext("/big") { ex ->
            ex.sendResponseHeaders(200, 0)
            ex.responseBody.use { out ->
                val chunk = ByteArray(65536) { 'a'.code.toByte() }
                try {
                    repeat(100) { out.write(chunk) }
                } catch (_: Exception) {
                    // client closed the connection once the limit was hit
                }
            }
        }
        server.start()
    }

    @AfterEach
    fun stop() {
        server.stop(0)
    }

    @Test
    fun `follows redirect to an allowed public url`() {
        assertEquals("<rss>ok</rss>", fetcher.fetch("$base/redirect-ok"))
    }

    @Test
    fun `refuses redirect to a blocked address`() {
        assertFailsWith<UnsafeUrlException> { fetcher.fetch("$base/redirect-blocked") }
    }

    @Test
    fun `refuses a blocked initial url`() {
        assertFailsWith<UnsafeUrlException> { fetcher.fetch("http://127.0.0.2:1/") }
    }

    @Test
    fun `stops after 5 redirects`() {
        assertFailsWith<IllegalStateException> { fetcher.fetch("$base/loop") }
    }

    @Test
    fun `rejects body over 5 MiB`() {
        assertFailsWith<FeedTooLargeException> { fetcher.fetch("$base/big") }
    }
}
