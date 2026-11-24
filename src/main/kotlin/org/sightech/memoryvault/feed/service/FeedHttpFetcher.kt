package org.sightech.memoryvault.feed.service

import org.sightech.memoryvault.security.FeedTooLargeException
import org.sightech.memoryvault.security.OutboundUrlValidator
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

@Component
class FeedHttpFetcher(private val validator: OutboundUrlValidator) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER)
        .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
        .build()

    fun fetch(url: String): String {
        var current: URI = validator.validate(url)
        var redirects = 0
        while (true) {
            val request = HttpRequest.newBuilder(current)
                .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
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
            return readBounded(response)
        }
    }

    private fun readBounded(response: HttpResponse<java.io.InputStream>): String {
        val declared = response.headers().firstValueAsLong("Content-Length").orElse(-1L)
        if (declared > MAX_BYTES) {
            response.body().close()
            throw FeedTooLargeException("Feed exceeds $MAX_BYTES bytes")
        }
        response.body().use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                total += n
                if (total > MAX_BYTES) throw FeedTooLargeException("Feed exceeds $MAX_BYTES bytes")
                out.write(buffer, 0, n)
            }
            return out.toString(Charsets.UTF_8)
        }
    }

    companion object {
        const val MAX_BYTES = 5 * 1024 * 1024
        const val MAX_REDIRECTS = 5
        const val CONNECT_TIMEOUT_SECONDS = 10L
        const val REQUEST_TIMEOUT_SECONDS = 20L
        const val USER_AGENT = "MemoryVault/1.0"
        private val REDIRECT_STATUSES = setOf(301, 302, 303, 307, 308)
    }
}
