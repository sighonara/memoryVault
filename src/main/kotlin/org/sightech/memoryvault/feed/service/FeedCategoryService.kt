package org.sightech.memoryvault.feed.service

import org.sightech.memoryvault.auth.CurrentUser
import org.sightech.memoryvault.feed.entity.FeedCategory
import org.sightech.memoryvault.feed.repository.FeedCategoryRepository
import org.sightech.memoryvault.feed.repository.FeedRepository
import org.sightech.memoryvault.websocket.ContentMutated
import org.sightech.memoryvault.websocket.ContentType
import org.sightech.memoryvault.websocket.MutationType
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class FeedCategoryService(
    private val categoryRepository: FeedCategoryRepository,
    private val feedRepository: FeedRepository,
    private val eventPublisher: ApplicationEventPublisher
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun listCategories(): List<FeedCategory> {
        val userId = CurrentUser.userId()
        return categoryRepository.findAllActiveByUserId(userId)
    }

    fun getCategoryById(categoryId: UUID): FeedCategory? {
        val userId = CurrentUser.userId()
        return categoryRepository.findActiveByIdAndUserId(categoryId, userId)
    }

    fun getSubscribedCategory(): FeedCategory {
        val userId = CurrentUser.userId()
        return categoryRepository.findActiveByUserIdAndNameIgnoreCase(userId, FeedCategory.SUBSCRIBED_NAME)
            ?: createCategory(FeedCategory.SUBSCRIBED_NAME)
    }

    /**
     * Returns Pair(category, wasCreated) so callers can track creation counts.
     */
    fun findOrCreateByName(name: String): Pair<FeedCategory, Boolean> {
        val userId = CurrentUser.userId()
        val existing = categoryRepository.findActiveByUserIdAndNameIgnoreCase(userId, name)
        return if (existing != null) {
            existing to false
        } else {
            createCategory(name) to true
        }
    }

    fun createCategory(name: String): FeedCategory {
        val userId = CurrentUser.userId()
        val maxSort = categoryRepository.findMaxSortOrderByUserId(userId)
        val category = FeedCategory(userId = userId, name = name, sortOrder = maxSort + 1)
        log.info("Created feed category '{}' for user {}", name, userId)
        val saved = categoryRepository.save(category)
        eventPublisher.publishEvent(ContentMutated(
            userId = userId, timestamp = Instant.now(),
            contentType = ContentType.CATEGORY, mutationType = MutationType.CREATED,
            entityId = saved.id
        ))
        return saved
    }

    fun renameCategory(categoryId: UUID, newName: String): FeedCategory? {
        val userId = CurrentUser.userId()
        val category = categoryRepository.findActiveByIdAndUserId(categoryId, userId) ?: return null
        if (category.name == FeedCategory.SUBSCRIBED_NAME) {
            throw IllegalArgumentException("Cannot rename the '${FeedCategory.SUBSCRIBED_NAME}' category")
        }
        category.name = newName
        category.updatedAt = Instant.now()
        log.info("Renamed feed category {} to '{}' for user {}", categoryId, newName, userId)
        val saved = categoryRepository.save(category)
        eventPublisher.publishEvent(ContentMutated(
            userId = userId, timestamp = Instant.now(),
            contentType = ContentType.CATEGORY, mutationType = MutationType.UPDATED,
            entityId = categoryId
        ))
        return saved
    }

    @Transactional
    fun deleteCategory(categoryId: UUID): Boolean {
        val userId = CurrentUser.userId()
        val category = categoryRepository.findActiveByIdAndUserId(categoryId, userId) ?: return false
        if (category.name == FeedCategory.SUBSCRIBED_NAME) {
            throw IllegalArgumentException("Cannot delete the '${FeedCategory.SUBSCRIBED_NAME}' category")
        }
        val subscribed = getSubscribedCategory()
        val moved = feedRepository.moveFeedsBetweenCategories(categoryId, subscribed.id, userId, Instant.now())
        log.info("Moved {} feed(s) from category {} to Subscribed before deletion", moved, categoryId)
        category.deletedAt = Instant.now()
        category.updatedAt = Instant.now()
        categoryRepository.save(category)
        log.info("Deleted feed category {} for user {}", categoryId, userId)
        eventPublisher.publishEvent(ContentMutated(
            userId = userId, timestamp = Instant.now(),
            contentType = ContentType.CATEGORY, mutationType = MutationType.DELETED,
            entityId = categoryId
        ))
        return true
    }

    @Transactional
    fun reorderCategories(categoryIds: List<UUID>): List<FeedCategory> {
        val userId = CurrentUser.userId()
        val categories = categoryRepository.findAllActiveByUserId(userId)
        val categoryMap = categories.associateBy { it.id }
        val now = Instant.now()
        categoryIds.forEachIndexed { index, id ->
            val cat = categoryMap[id] ?: return@forEachIndexed
            cat.sortOrder = index
            cat.updatedAt = now
        }
        log.info("Reordered {} feed categories for user {}", categoryIds.size, userId)
        val saved = categoryRepository.saveAll(categories)
        eventPublisher.publishEvent(ContentMutated(
            userId = userId, timestamp = Instant.now(),
            contentType = ContentType.CATEGORY, mutationType = MutationType.UPDATED
        ))
        return saved
    }
}
