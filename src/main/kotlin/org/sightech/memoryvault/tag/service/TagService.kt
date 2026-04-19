package org.sightech.memoryvault.tag.service
 
import org.sightech.memoryvault.auth.CurrentUser
import org.sightech.memoryvault.tag.entity.Tag
import org.sightech.memoryvault.tag.repository.TagRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class TagService(private val repository: TagRepository) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun findOrCreateByName(name: String): Tag {
        val userId = CurrentUser.userId()
        return repository.findByUserIdAndName(userId, name)
            ?: repository.save(Tag(userId = userId, name = name)).also {
                log.info("Created tag name={} tagId={}", name, it.id)
            }
    }

    fun findOrCreateByNames(names: List<String>): List<Tag> {
        val userId = CurrentUser.userId()
        val existing = repository.findByUserIdAndNameIn(userId, names)
        val existingNames = existing.map { it.name }.toSet()
        val newTags = names.filter { it !in existingNames }
            .map { repository.save(Tag(userId = userId, name = it)) }
        if (newTags.isNotEmpty()) {
            log.info("Created {} new tag(s)", newTags.size)
        }
        return existing + newTags
    }
}
