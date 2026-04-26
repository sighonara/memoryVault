package org.sightech.memoryvault.youtube.repository

import org.sightech.memoryvault.youtube.entity.Video
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface VideoRepository : JpaRepository<Video, UUID> {

    @Query("SELECT v FROM Video v LEFT JOIN FETCH v.tags WHERE v.youtubeList.id = :listId AND v.youtubeList.userId = :userId ORDER BY v.createdAt DESC")
    fun findByYoutubeListIdAndUserId(listId: UUID, userId: UUID): List<Video>

    @Query("SELECT v FROM Video v LEFT JOIN FETCH v.tags WHERE v.youtubeList.id = :listId AND v.youtubeList.userId = :userId AND v.removedFromYoutube = true ORDER BY v.removedDetectedAt DESC")
    fun findRemovedByYoutubeListIdAndUserId(listId: UUID, userId: UUID): List<Video>

    @Query("SELECT v FROM Video v WHERE v.id = :id AND v.youtubeList.userId = :userId")
    fun findByIdAndUserId(id: UUID, userId: UUID): Video?

    fun existsByYoutubeListIdAndYoutubeVideoId(youtubeListId: UUID, youtubeVideoId: String): Boolean

    fun findByYoutubeListIdAndYoutubeVideoId(youtubeListId: UUID, youtubeVideoId: String): Video?

    @Query("SELECT COUNT(v) FROM Video v WHERE v.youtubeList.id = :listId AND v.youtubeList.userId = :userId")
    fun countByYoutubeListIdAndUserId(listId: UUID, userId: UUID): Long

    @Query("SELECT COUNT(v) FROM Video v WHERE v.youtubeList.id = :listId AND v.youtubeList.userId = :userId AND v.downloadedAt IS NOT NULL")
    fun countDownloadedByYoutubeListIdAndUserId(listId: UUID, userId: UUID): Long

    @Query("SELECT COUNT(v) FROM Video v WHERE v.youtubeList.id = :listId AND v.youtubeList.userId = :userId AND v.removedFromYoutube = true")
    fun countRemovedByYoutubeListIdAndUserId(listId: UUID, userId: UUID): Long

    @Query("SELECT v FROM Video v LEFT JOIN FETCH v.tags WHERE v.youtubeList.id IN :listIds AND v.youtubeVideoId IN :videoIds")
    fun findByYoutubeListIdInAndYoutubeVideoIdIn(listIds: List<UUID>, videoIds: List<String>): List<Video>

    fun countByYoutubeListUserIdAndYoutubeListDeletedAtIsNull(userId: UUID): Long

    fun countByYoutubeListUserIdAndYoutubeListDeletedAtIsNullAndDownloadedAtIsNotNull(userId: UUID): Long

    fun countByYoutubeListUserIdAndYoutubeListDeletedAtIsNullAndRemovedFromYoutubeTrue(userId: UUID): Long

    @Query("SELECT v.id FROM Video v WHERE v.youtubeList.userId = :userId AND v.youtubeList.deletedAt IS NULL AND v.downloadedAt IS NOT NULL")
    fun findDownloadedVideoIdsByUserId(userId: UUID): List<UUID>
}
