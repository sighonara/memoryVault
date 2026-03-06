package org.sightech.memoryvault.youtube.service

import java.util.UUID

interface VideoDownloader {
    fun download(youtubeUrl: String, videoId: UUID): DownloadResult
}
