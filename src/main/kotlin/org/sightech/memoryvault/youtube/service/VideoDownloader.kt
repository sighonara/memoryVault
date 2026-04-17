package org.sightech.memoryvault.youtube.service

import java.util.UUID

// Fire-and-forget dispatch. Implementations must persist their own result on the Video
// entity (own @Transactional) because the return value is discarded by @Async proxies.
interface VideoDownloader {
    fun download(youtubeUrl: String, videoId: UUID)
}
