package org.sightech.memoryvault.tag.service

import org.sightech.memoryvault.tag.entity.Tag
import org.sightech.memoryvault.tag.repository.TagRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class TagService(private val repository: TagRepository) {

    companion object {
        val SYSTEM_USER_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    }

    fun findOrCreateByName(name: String): Tag {
        return repository.findByUserIdAndName(SYSTEM_USER_ID, name)
            ?: repository.save(Tag(userId = SYSTEM_USER_ID, name = name))
    }

    fun findOrCreateByNames(names: List<String>): List<Tag> {
        val existing = repository.findByUserIdAndNameIn(SYSTEM_USER_ID, names)
        val existingNames = existing.map { it.name }.toSet()
        val newTags = names.filter { it !in existingNames }
            .map { repository.save(Tag(userId = SYSTEM_USER_ID, name = it)) }
        return existing + newTags
    }
}
