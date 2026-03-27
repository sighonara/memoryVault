package org.sightech.memoryvault.mcp

import org.sightech.memoryvault.feed.service.FeedCategoryService
import org.sightech.memoryvault.feed.service.FeedItemService
import org.sightech.memoryvault.feed.service.FeedService
import org.springframework.ai.tool.annotation.Tool
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class FeedCategoryTools(
    private val feedCategoryService: FeedCategoryService,
    private val feedService: FeedService,
    private val feedItemService: FeedItemService
) {

    @Tool(description = "Create a new feed category. Use when the user wants to organize feeds into groups like 'Tech', 'News', etc.")
    fun addCategory(name: String): String {
        val category = feedCategoryService.createCategory(name)
        return "Created category: \"${category.name}\" — id: ${category.id}"
    }

    @Tool(description = "List all feed categories with their feed counts. Use when the user wants to see how their feeds are organized.")
    fun listCategories(): String {
        val categories = feedCategoryService.listCategories()
        if (categories.isEmpty()) return "No categories found."
        val lines = categories.map { cat ->
            val feeds = feedService.listFeedsByCategory(cat.id)
            "- ${cat.name} (${feeds.size} feed(s), sortOrder: ${cat.sortOrder}) — id: ${cat.id}"
        }
        return "${categories.size} category/categories:\n${lines.joinToString("\n")}"
    }

    @Tool(description = "Rename a feed category. Use when the user wants to change the name of a category. Cannot rename the 'Subscribed' category.")
    fun renameCategory(categoryId: String, name: String): String {
        val category = feedCategoryService.renameCategory(UUID.fromString(categoryId), name)
            ?: return "Category not found."
        return "Renamed to: \"${category.name}\""
    }

    @Tool(description = "Delete a feed category. Moves all its feeds to the 'Subscribed' category. Use when the user wants to remove a category. Cannot delete 'Subscribed'.")
    fun deleteCategory(categoryId: String): String {
        val deleted = feedCategoryService.deleteCategory(UUID.fromString(categoryId))
        return if (deleted) "Category deleted. Feeds moved to Subscribed." else "Category not found."
    }

    @Tool(description = "Move a feed to a different category. Use when the user wants to reorganize their feeds.")
    fun moveFeedToCategory(feedId: String, categoryId: String): String {
        val feed = feedService.moveFeedToCategory(UUID.fromString(feedId), UUID.fromString(categoryId))
            ?: return "Feed or category not found."
        return "Moved \"${feed.title ?: feed.url}\" to new category."
    }

    @Tool(description = "Reorder feed categories. Pass a list of category IDs in the desired order. Use when the user wants to change the order categories appear in.")
    fun reorderCategories(categoryIds: List<String>): String {
        val uuids = categoryIds.map { UUID.fromString(it) }
        feedCategoryService.reorderCategories(uuids)
        return "Categories reordered."
    }

    @Tool(description = "Mark all items in all feeds within a category as read. Use when the user wants to clear all unread items in a category at once.")
    fun markCategoryRead(categoryId: String): String {
        val count = feedItemService.markCategoryRead(UUID.fromString(categoryId))
        return "Marked $count item(s) as read."
    }
}
