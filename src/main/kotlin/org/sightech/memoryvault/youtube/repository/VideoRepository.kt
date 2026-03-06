package org.sightech.memoryvault.youtube.repository

import org.sightech.memoryvault.youtube.entity.Video
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface VideoRepository : JpaRepository<Video, UUID> {

    @Query("SELECT v FROM Video v LEFT JOIN FETCH v.tags WHERE v.youtubeList.id = :listId ORDER BY v.createdAt DESC")
    fun findByYoutubeListId(listId: UUID): List<Video>

    @Query("SELECT v FROM Video v LEFT JOIN FETCH v.tags WHERE v.youtubeList.id = :listId AND v.removedFromYoutube = true ORDER BY v.removedDetectedAt DESC")
    fun findRemovedByYoutubeListId(listId: UUID): List<Video>

    fun existsByYoutubeListIdAndYoutubeVideoId(youtubeListId: UUID, youtubeVideoId: String): Boolean

    fun findByYoutubeListIdAndYoutubeVideoId(youtubeListId: UUID, youtubeVideoId: String): Video?

    @Query("SELECT COUNT(v) FROM Video v WHERE v.youtubeList.id = :listId")
    fun countByYoutubeListId(listId: UUID): Long

    @Query("SELECT COUNT(v) FROM Video v WHERE v.youtubeList.id = :listId AND v.downloadedAt IS NOT NULL")
    fun countDownloadedByYoutubeListId(listId: UUID): Long

    @Query("SELECT COUNT(v) FROM Video v WHERE v.youtubeList.id = :listId AND v.removedFromYoutube = true")
    fun countRemovedByYoutubeListId(listId: UUID): Long

    @Query("SELECT v FROM Video v LEFT JOIN FETCH v.tags WHERE v.youtubeList.id IN :listIds AND v.youtubeVideoId IN :videoIds")
    fun findByYoutubeListIdInAndYoutubeVideoIdIn(listIds: List<UUID>, videoIds: List<String>): List<Video>

    fun countByYoutubeListUserIdAndYoutubeListDeletedAtIsNull(userId: UUID): Long

    fun countByYoutubeListUserIdAndYoutubeListDeletedAtIsNullAndDownloadedAtIsNotNull(userId: UUID): Long

    fun countByYoutubeListUserIdAndYoutubeListDeletedAtIsNullAndRemovedFromYoutubeTrue(userId: UUID): Long
}
