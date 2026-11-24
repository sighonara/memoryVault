package org.sightech.memoryvault.youtube.service

import java.net.URI

object YoutubeUrlValidator {

    fun requireYoutubeUrl(url: String): String {
        val uri = try {
            URI(url)
        } catch (e: Exception) {
            throw IllegalArgumentException("Not a YouTube URL")
        }
        val host = uri.host?.lowercase()
        val hostOk = host != null &&
            (host == "youtube.com" || host.endsWith(".youtube.com") || host == "youtu.be")
        if (uri.scheme?.lowercase() != "https" || !hostOk) {
            throw IllegalArgumentException("Not a YouTube URL")
        }
        return url
    }
}
