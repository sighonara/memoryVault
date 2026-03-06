package org.sightech.memoryvault.search

import org.springframework.stereotype.Service
import java.util.UUID

enum class ContentType { BOOKMARK, FEED_ITEM, VIDEO }

data class SearchResult(
    val type: ContentType,
    val id: UUID,
    val title: String?,
    val url: String?,
    val rank: Float
)

@Service
class SearchService(private val searchRepository: SearchRepository) {

    fun search(query: String, types: List<ContentType>?, userId: UUID, limit: Int): List<SearchResult> {
        val searchTypes = types ?: ContentType.entries

        val results = mutableListOf<SearchResult>()

        if (ContentType.BOOKMARK in searchTypes) {
            results.addAll(searchRepository.searchBookmarks(query, userId, limit))
        }
        if (ContentType.FEED_ITEM in searchTypes) {
            results.addAll(searchRepository.searchFeedItems(query, userId, limit))
        }
        if (ContentType.VIDEO in searchTypes) {
            results.addAll(searchRepository.searchVideos(query, userId, limit))
        }

        return results.sortedByDescending { it.rank }.take(limit)
    }
}
