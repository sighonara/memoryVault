package org.sightech.memoryvault.graphql

import kotlinx.coroutines.runBlocking
import org.sightech.memoryvault.auth.CurrentUser
import org.sightech.memoryvault.auth.repository.UserRepository
import org.sightech.memoryvault.feed.entity.FeedCategory
import org.sightech.memoryvault.feed.service.FeedCategoryService
import org.sightech.memoryvault.feed.service.FeedItemService
import org.sightech.memoryvault.feed.service.FeedService
import org.sightech.memoryvault.feed.service.ImportResult
import org.sightech.memoryvault.feed.service.OpmlService
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.stereotype.Controller
import java.time.Instant
import java.util.UUID

@Controller
class FeedCategoryResolver(
    private val feedCategoryService: FeedCategoryService,
    private val feedService: FeedService,
    private val feedItemService: FeedItemService,
    private val opmlService: OpmlService,
    private val userRepository: UserRepository
) {

    @QueryMapping
    fun feedCategories(): List<Map<String, Any?>> {
        val categories = feedCategoryService.listCategories()
        return categories.map { category ->
            val feedsWithUnread = feedService.listFeedsByCategory(category.id)
            val totalUnread = feedsWithUnread.sumOf { it.second }
            mapOf(
                "category" to category,
                "feeds" to feedsWithUnread.map { (feed, unread) -> mapOf("feed" to feed, "unreadCount" to unread) },
                "totalUnread" to totalUnread
            )
        }
    }

    @QueryMapping
    fun feedItemsByCategory(
        @Argument categoryId: UUID,
        @Argument limit: Int?,
        @Argument unreadOnly: Boolean?,
        @Argument sortOrder: String?
    ): List<org.sightech.memoryvault.feed.entity.FeedItem> {
        val feeds = feedService.listFeedsByCategory(categoryId)
        val feedIds = feeds.map { it.first.id }
        return feedItemService.getItemsByFeedIds(feedIds, limit, unreadOnly ?: false, sortOrder ?: "NEWEST_FIRST")
    }

    @QueryMapping
    fun feedItemsAll(
        @Argument limit: Int?,
        @Argument unreadOnly: Boolean?,
        @Argument sortOrder: String?
    ): List<org.sightech.memoryvault.feed.entity.FeedItem> {
        val feeds = feedService.listFeeds()
        val feedIds = feeds.map { it.first.id }
        return feedItemService.getItemsByFeedIds(feedIds, limit, unreadOnly ?: false, sortOrder ?: "NEWEST_FIRST")
    }

    @QueryMapping
    fun exportFeeds(): String {
        return opmlService.exportOpml()
    }

    @QueryMapping
    fun userPreferences(): Map<String, String> {
        val userId = CurrentUser.userId()
        val user = userRepository.findById(userId).orElseThrow()
        return mapOf("viewMode" to user.viewMode, "sortOrder" to user.sortOrder)
    }

    @MutationMapping
    fun addCategory(@Argument name: String): FeedCategory {
        return feedCategoryService.createCategory(name)
    }

    @MutationMapping
    fun renameCategory(@Argument categoryId: UUID, @Argument name: String): FeedCategory? {
        return feedCategoryService.renameCategory(categoryId, name)
    }

    @MutationMapping
    fun deleteCategory(@Argument categoryId: UUID): Boolean {
        return feedCategoryService.deleteCategory(categoryId)
    }

    @MutationMapping
    fun reorderCategories(@Argument categoryIds: List<UUID>): List<FeedCategory> {
        return feedCategoryService.reorderCategories(categoryIds)
    }

    @MutationMapping
    fun moveFeedToCategory(@Argument feedId: UUID, @Argument categoryId: UUID): org.sightech.memoryvault.feed.entity.Feed? {
        return feedService.moveFeedToCategory(feedId, categoryId)
    }

    @MutationMapping
    fun markCategoryRead(@Argument categoryId: UUID): Int {
        return feedItemService.markCategoryRead(categoryId)
    }

    @MutationMapping
    fun importFeeds(@Argument opml: String): ImportResult {
        return runBlocking { opmlService.importOpml(opml) }
    }

    @MutationMapping
    fun updateUserPreferences(@Argument viewMode: String?, @Argument sortOrder: String?): Map<String, String> {
        val userId = CurrentUser.userId()
        val user = userRepository.findById(userId).orElseThrow()
        if (viewMode != null) user.viewMode = viewMode
        if (sortOrder != null) user.sortOrder = sortOrder
        user.updatedAt = Instant.now()
        userRepository.save(user)
        return mapOf("viewMode" to user.viewMode, "sortOrder" to user.sortOrder)
    }
}
