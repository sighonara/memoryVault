package org.sightech.memoryvault.tag.service
 
import org.sightech.memoryvault.auth.CurrentUser
import org.sightech.memoryvault.tag.entity.Tag
import org.sightech.memoryvault.tag.repository.TagRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class TagService(private val repository: TagRepository) {

    fun findOrCreateByName(name: String): Tag {
        val userId = CurrentUser.userId()
        return repository.findByUserIdAndName(userId, name)
            ?: repository.save(Tag(userId = userId, name = name))
    }

    fun findOrCreateByNames(names: List<String>): List<Tag> {
        val userId = CurrentUser.userId()
        val existing = repository.findByUserIdAndNameIn(userId, names)
        val existingNames = existing.map { it.name }.toSet()
        val newTags = names.filter { it !in existingNames }
            .map { repository.save(Tag(userId = userId, name = it)) }
        return existing + newTags
    }
}
