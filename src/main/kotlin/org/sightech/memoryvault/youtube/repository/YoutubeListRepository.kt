package org.sightech.memoryvault.youtube.repository

import org.sightech.memoryvault.youtube.entity.YoutubeList
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface YoutubeListRepository : JpaRepository<YoutubeList, UUID> {

    @Query("SELECT yl FROM YoutubeList yl WHERE yl.deletedAt IS NULL AND yl.userId = :userId ORDER BY yl.name")
    fun findAllActiveByUserId(userId: UUID): List<YoutubeList>

    @Query("SELECT yl FROM YoutubeList yl WHERE yl.id = :id AND yl.userId = :userId AND yl.deletedAt IS NULL")
    fun findActiveByIdAndUserId(id: UUID, userId: UUID): YoutubeList?

    fun countByUserIdAndDeletedAtIsNull(userId: UUID): Long

    fun countByUserIdAndDeletedAtIsNullAndFailureCountGreaterThan(userId: UUID, failureCount: Int): Long
}
