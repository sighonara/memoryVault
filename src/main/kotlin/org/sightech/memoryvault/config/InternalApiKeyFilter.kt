package org.sightech.memoryvault.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.security.MessageDigest

@Component
@Profile("aws")
class InternalApiKeyFilter(
    @Value("\${memoryvault.internal.api-key}")
    private val apiKey: String
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(javaClass)
    private val apiKeyBytes: ByteArray = apiKey.toByteArray(Charsets.UTF_8)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        if (request.requestURI.startsWith("/api/internal/")) {
            val providedKey = request.getHeader("X-Internal-Key")
            if (providedKey == null || !constantTimeEquals(providedKey.toByteArray(Charsets.UTF_8), apiKeyBytes)) {
                // Never log the attempted key (would leak guesses into logs); do log URI + remote
                // so repeated probes show up in log analysis.
                log.warn(
                    "Rejected internal request: uri={} remote={} keyProvided={}",
                    request.requestURI,
                    request.remoteAddr,
                    providedKey != null
                )
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid API key")
                return
            }
        }
        filterChain.doFilter(request, response)
    }

    // Length-hiding constant-time comparison. MessageDigest.isEqual short-circuits on length
    // mismatch (revealing key length via timing); we hash both sides first so every call takes
    // the same time regardless of the provided key's length.
    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        val digest = MessageDigest.getInstance("SHA-256")
        val aHash = digest.digest(a)
        val bHash = MessageDigest.getInstance("SHA-256").digest(b)
        return MessageDigest.isEqual(aHash, bHash)
    }
}
