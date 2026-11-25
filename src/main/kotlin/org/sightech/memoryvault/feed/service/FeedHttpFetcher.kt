package org.sightech.memoryvault.feed.service

import org.sightech.memoryvault.security.FeedTooLargeException
import org.sightech.memoryvault.security.OutboundUrlValidator
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.Charset
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@Component
class FeedHttpFetcher(
    private val validator: OutboundUrlValidator,
    private val totalTimeout: Duration = Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS)
) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER)
        .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
        .build()

    private val watchdog = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "feed-fetch-watchdog").apply { isDaemon = true }
    }

    /** Fetches the feed; the whole exchange, including the body read, must finish within [totalTimeout]. */
    fun fetch(url: String): String {
        val deadline = System.nanoTime() + totalTimeout.toNanos()
        var current: URI = validator.validate(url)
        var redirects = 0
        while (true) {
            val remaining = deadline - System.nanoTime()
            if (remaining <= 0) throw IllegalStateException("Feed fetch timed out")
            val request = HttpRequest.newBuilder(current)
                .timeout(Duration.ofNanos(remaining))
                .header("User-Agent", USER_AGENT)
                .GET()
                .build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
            val status = response.statusCode()
            if (status in REDIRECT_STATUSES) {
                response.body().close()
                val location = response.headers().firstValue("Location").orElse(null)
                    ?: throw IllegalStateException("Redirect without Location header")
                if (++redirects > MAX_REDIRECTS) {
                    throw IllegalStateException("Too many redirects")
                }
                current = validator.validate(current.resolve(location).toString())
                log.debug("Following feed redirect {} to host={}", redirects, current.host)
                continue
            }
            if (status !in 200..299) {
                response.body().close()
                throw IllegalStateException("Unexpected HTTP status $status")
            }
            return readBounded(response, deadline)
        }
    }

    private fun readBounded(response: HttpResponse<InputStream>, deadline: Long): String {
        val declared = response.headers().firstValueAsLong("Content-Length").orElse(-1L)
        if (declared > MAX_BYTES) {
            response.body().close()
            throw FeedTooLargeException("Feed exceeds $MAX_BYTES bytes")
        }
        val timedOut = AtomicBoolean(false)
        val input = response.body()
        // A read blocked on a stalled connection cannot check the clock, so close the stream at the deadline.
        val task = watchdog.schedule({
            timedOut.set(true)
            runCatching { input.close() }
        }, (deadline - System.nanoTime()).coerceAtLeast(0), TimeUnit.NANOSECONDS)
        try {
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                if (timedOut.get() || System.nanoTime() > deadline) throw IllegalStateException("Feed fetch timed out")
                val n = input.read(buffer)
                if (n < 0) break
                total += n
                if (total > MAX_BYTES) throw FeedTooLargeException("Feed exceeds $MAX_BYTES bytes")
                out.write(buffer, 0, n)
            }
            return decode(out.toByteArray(), response.headers().firstValue("Content-Type").orElse(null))
        } catch (e: java.io.IOException) {
            if (timedOut.get()) throw IllegalStateException("Feed fetch timed out")
            throw e
        } finally {
            task.cancel(false)
            runCatching { input.close() }
        }
    }

    companion object {
        const val MAX_BYTES = 5 * 1024 * 1024
        const val MAX_REDIRECTS = 5
        const val CONNECT_TIMEOUT_SECONDS = 10L
        const val REQUEST_TIMEOUT_SECONDS = 20L
        const val USER_AGENT = "MemoryVault/1.0"
        private val REDIRECT_STATUSES = setOf(301, 302, 303, 307, 308)
        private val CONTENT_TYPE_CHARSET = Regex("charset\\s*=\\s*\"?([A-Za-z0-9_.:-]+)", RegexOption.IGNORE_CASE)
        private val PROLOG_ENCODING = Regex("^\\s*<\\?xml[^>]*?encoding\\s*=\\s*[\"']([A-Za-z0-9_.:-]+)[\"']", RegexOption.IGNORE_CASE)

        /** Charset order: Content-Type header, XML prolog, byte-order mark, then UTF-8. */
        internal fun decode(bytes: ByteArray, contentType: String?): String {
            val charset = fromContentType(contentType) ?: fromProlog(bytes) ?: fromBom(bytes) ?: Charsets.UTF_8
            return String(bytes, charset).removePrefix("\uFEFF")
        }

        private fun lookup(name: String): Charset? = try {
            Charset.forName(name)
        } catch (e: Exception) {
            null
        }

        private fun fromContentType(contentType: String?): Charset? =
            contentType?.let { CONTENT_TYPE_CHARSET.find(it)?.groupValues?.get(1) }?.let(::lookup)

        private fun fromProlog(bytes: ByteArray): Charset? {
            val head = String(bytes, 0, minOf(bytes.size, 200), Charsets.ISO_8859_1).removePrefix("\u00EF\u00BB\u00BF")
            return PROLOG_ENCODING.find(head)?.groupValues?.get(1)?.let(::lookup)
        }

        private fun fromBom(bytes: ByteArray): Charset? {
            fun b(i: Int) = bytes.getOrNull(i)?.toInt()?.and(0xff)
            return when {
                b(0) == 0xEF && b(1) == 0xBB && b(2) == 0xBF -> Charsets.UTF_8
                b(0) == 0xFE && b(1) == 0xFF -> Charsets.UTF_16BE
                b(0) == 0xFF && b(1) == 0xFE -> Charsets.UTF_16LE
                else -> null
            }
        }
    }
}
