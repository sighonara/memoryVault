package org.sightech.memoryvault.tag.repository

import org.sightech.memoryvault.tag.entity.Tag
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface TagRepository : JpaRepository<Tag, UUID> {

    fun findByUserIdAndNameIn(userId: UUID, names: List<String>): List<Tag>

    fun findByUserIdAndName(userId: UUID, name: String): Tag?
}
