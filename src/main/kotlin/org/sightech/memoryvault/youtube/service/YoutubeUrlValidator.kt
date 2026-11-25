package org.sightech.memoryvault.youtube.service

import org.sightech.memoryvault.security.InvalidYoutubeUrlException
import java.net.URI

object YoutubeUrlValidator {

    private const val MESSAGE = "Not a YouTube URL"

    /**
     * Returns the URL to hand to yt-dlp. A scheme-less or http:// URL is upgraded to https://
     * first; the result is only accepted when it is an https URL on a YouTube host.
     */
    fun requireYoutubeUrl(url: String): String {
        val normalised = normaliseScheme(url)
        val uri = try {
            URI(normalised)
        } catch (e: Exception) {
            throw InvalidYoutubeUrlException(MESSAGE)
        }
        val host = uri.host?.lowercase()
        val hostOk = host != null &&
            (host == "youtube.com" || host.endsWith(".youtube.com") || host == "youtu.be")
        if (uri.scheme?.lowercase() != "https" || !hostOk) {
            throw InvalidYoutubeUrlException(MESSAGE)
        }
        return normalised
    }

    private fun normaliseScheme(url: String): String = when {
        url.startsWith("http://", ignoreCase = true) -> "https://" + url.substring("http://".length)
        !url.contains("://") && !url.startsWith("-") -> "https://$url"
        else -> url
    }
}
