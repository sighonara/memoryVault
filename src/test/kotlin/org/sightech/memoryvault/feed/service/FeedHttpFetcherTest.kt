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
import kotlin.test.assertTrue

class FeedHttpFetcherTest {

    private lateinit var server: HttpServer
    private var base = ""
    private val loopHits = java.util.concurrent.atomic.AtomicInteger()

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
            loopHits.incrementAndGet()
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
        server.createContext("/latin1-header") { ex ->
            ex.responseHeaders.add("Content-Type", "application/rss+xml; charset=ISO-8859-1")
            respond(ex, "<rss>café</rss>".toByteArray(Charsets.ISO_8859_1))
        }
        server.createContext("/latin1-prolog") { ex ->
            ex.responseHeaders.add("Content-Type", "application/xml")
            respond(ex, "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><rss>café</rss>".toByteArray(Charsets.ISO_8859_1))
        }
        server.createContext("/utf16-bom") { ex ->
            val bom = byteArrayOf(0xFE.toByte(), 0xFF.toByte())
            respond(ex, bom + "<rss>café</rss>".toByteArray(Charsets.UTF_16BE))
        }
        server.createContext("/drip") { ex ->
            ex.sendResponseHeaders(200, 1000)
            try {
                ex.responseBody.use { out ->
                    repeat(1000) {
                        out.write('a'.code)
                        out.flush()
                        Thread.sleep(100)
                    }
                }
            } catch (_: Exception) {
                // client gave up
            }
        }
        server.start()
    }

    private fun respond(ex: com.sun.net.httpserver.HttpExchange, body: ByteArray) {
        ex.sendResponseHeaders(200, body.size.toLong())
        ex.responseBody.use { it.write(body) }
    }

    @Test
    fun `decodes charset declared in Content-Type`() {
        assertEquals("<rss>café</rss>", fetcher.fetch("$base/latin1-header"))
    }

    @Test
    fun `decodes charset declared only in the XML prolog`() {
        assertEquals("<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><rss>café</rss>", fetcher.fetch("$base/latin1-prolog"))
    }

    @Test
    fun `decodes UTF-16 by byte-order mark`() {
        assertEquals("<rss>café</rss>", fetcher.fetch("$base/utf16-bom"))
    }

    @Test
    fun `aborts a body that drips past the total deadline`() {
        val quick = FeedHttpFetcher(validator, java.time.Duration.ofMillis(700))
        val start = System.nanoTime()
        val ex = assertFailsWith<IllegalStateException> { quick.fetch("$base/drip") }
        assertTrue(ex.message!!.contains("timed out"), ex.message)
        assertTrue((System.nanoTime() - start) / 1_000_000 < 5_000, "should abort well before the body finishes")
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
        val ex = assertFailsWith<IllegalStateException> { fetcher.fetch("$base/loop") }
        assertEquals("Too many redirects", ex.message)
        assertEquals(6, loopHits.get())
    }

    @Test
    fun `rejects body over 5 MiB`() {
        assertFailsWith<FeedTooLargeException> { fetcher.fetch("$base/big") }
    }
}
