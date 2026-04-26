package org.sightech.memoryvault.backup.provider

import java.io.InputStream

data class BackupSearchResult(
    val externalId: String,
    val externalUrl: String
)

data class BackupUploadResult(
    val externalId: String,
    val externalUrl: String
)

data class VideoBackupMetadata(
    val youtubeVideoId: String,
    val title: String?,
    val description: String?,
    val youtubeUrl: String
)

interface BackupProvider {
    fun search(youtubeVideoId: String): BackupSearchResult?
    fun upload(videoFile: InputStream, metadata: VideoBackupMetadata): BackupUploadResult
    fun checkHealth(externalUrl: String): Boolean
    fun delete(externalId: String): Boolean
}
