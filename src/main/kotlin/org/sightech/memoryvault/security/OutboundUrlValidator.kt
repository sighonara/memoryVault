package org.sightech.memoryvault.security

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI

class UnsafeUrlException(message: String) : RuntimeException(message)

class InvalidYoutubeUrlException(message: String) : IllegalArgumentException(message)

class FeedTooLargeException(message: String) : RuntimeException(message)

/**
 * Validates URLs the server is about to fetch on a user's behalf. Only http(s) URLs whose
 * every resolved address is publicly routable are accepted.
 *
 * Redirect hops are each re-validated before they are followed.
 */
@Component
class OutboundUrlValidator(
    private val resolve: (String) -> List<InetAddress> = { InetAddress.getAllByName(it).toList() },
    private val isBlocked: (InetAddress) -> Boolean = ::isBlockedAddress
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun validate(url: String): URI {
        val uri = try {
            URI(url.trim())
        } catch (e: Exception) {
            throw UnsafeUrlException("Invalid URL")
        }
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            throw UnsafeUrlException("Only http and https URLs are allowed")
        }
        val host = uri.host
        if (host.isNullOrBlank()) {
            throw UnsafeUrlException("URL must have a host")
        }
        val addresses = try {
            resolve(host.removePrefix("[").removeSuffix("]"))
        } catch (e: Exception) {
            throw UnsafeUrlException("Could not resolve host")
        }
        if (addresses.isEmpty()) {
            throw UnsafeUrlException("Could not resolve host")
        }
        if (addresses.any { isBlocked(it) }) {
            log.warn("Rejected outbound URL host={}: resolves to a non-public address", host)
            throw UnsafeUrlException("URL points to a non-public address")
        }
        return uri
    }

    companion object {
        fun isBlockedAddress(address: InetAddress): Boolean {
            if (address.isLoopbackAddress || address.isAnyLocalAddress || address.isLinkLocalAddress ||
                address.isSiteLocalAddress || address.isMulticastAddress
            ) return true
            val bytes = address.address
            return when (address) {
                is Inet4Address -> {
                    val b0 = bytes[0].toInt() and 0xff
                    val b1 = bytes[1].toInt() and 0xff
                    b0 == 0 || (b0 == 100 && b1 in 64..127)
                }
                is Inet6Address -> (bytes[0].toInt() and 0xfe) == 0xfc
                else -> true
            }
        }
    }
}
