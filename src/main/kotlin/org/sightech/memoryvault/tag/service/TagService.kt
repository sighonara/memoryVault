package org.sightech.memoryvault.tag.service

import org.sightech.memoryvault.tag.entity.Tag
import org.sightech.memoryvault.tag.repository.TagRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class TagService(private val repository: TagRepository) {

    fun findOrCreateByName(userId: UUID, name: String): Tag {
        return repository.findByUserIdAndName(userId, name)
            ?: repository.save(Tag(userId = userId, name = name))
    }

    fun findOrCreateByNames(userId: UUID, names: List<String>): List<Tag> {
        val existing = repository.findByUserIdAndNameIn(userId, names)
        val existingNames = existing.map { it.name }.toSet()
        val newTags = names.filter { it !in existingNames }
            .map { repository.save(Tag(userId = userId, name = it)) }
        return existing + newTags
    }
}
