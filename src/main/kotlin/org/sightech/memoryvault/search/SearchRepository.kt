package org.sightech.memoryvault.search

import jakarta.persistence.EntityManager
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class SearchRepository(private val entityManager: EntityManager) {

    fun searchBookmarks(query: String, userId: UUID, limit: Int): List<SearchResult> {
        val tsQuery = toTsQuery(query)
        val sql = """
            SELECT id, title, url, ts_rank(search_vector, to_tsquery('english', :query)) AS rank
            FROM bookmarks
            WHERE user_id = :userId AND deleted_at IS NULL
              AND search_vector @@ to_tsquery('english', :query)
            ORDER BY rank DESC
            LIMIT :limit
        """.trimIndent()

        return executeSearch(sql, tsQuery, userId, limit, ContentType.BOOKMARK)
    }

    fun searchFeedItems(query: String, userId: UUID, limit: Int): List<SearchResult> {
        val tsQuery = toTsQuery(query)
        val sql = """
            SELECT fi.id, fi.title, fi.url, ts_rank(fi.search_vector, to_tsquery('english', :query)) AS rank
            FROM feed_items fi
            JOIN feeds f ON fi.feed_id = f.id
            WHERE f.user_id = :userId AND f.deleted_at IS NULL
              AND fi.search_vector @@ to_tsquery('english', :query)
            ORDER BY rank DESC
            LIMIT :limit
        """.trimIndent()

        return executeSearch(sql, tsQuery, userId, limit, ContentType.FEED_ITEM)
    }

    fun searchVideos(query: String, userId: UUID, limit: Int): List<SearchResult> {
        val tsQuery = toTsQuery(query)
        val sql = """
            SELECT v.id, v.title, v.youtube_url AS url, ts_rank(v.search_vector, to_tsquery('english', :query)) AS rank
            FROM videos v
            JOIN youtube_lists yl ON v.youtube_list_id = yl.id
            WHERE yl.user_id = :userId AND yl.deleted_at IS NULL
              AND v.search_vector @@ to_tsquery('english', :query)
            ORDER BY rank DESC
            LIMIT :limit
        """.trimIndent()

        return executeSearch(sql, tsQuery, userId, limit, ContentType.VIDEO)
    }

    private fun executeSearch(sql: String, query: String, userId: UUID, limit: Int, type: ContentType): List<SearchResult> {
        @Suppress("UNCHECKED_CAST")
        val results = entityManager.createNativeQuery(sql)
            .setParameter("query", query)
            .setParameter("userId", userId)
            .setParameter("limit", limit)
            .resultList as List<Array<Any>>

        return results.map { row ->
            SearchResult(
                type = type,
                id = row[0] as UUID,
                title = row[1] as? String,
                url = row[2] as? String,
                rank = (row[3] as Number).toFloat()
            )
        }
    }

    private fun toTsQuery(input: String): String {
        return input.trim().split("\\s+".toRegex())
            .filter { it.isNotBlank() }
            .joinToString(" & ")
    }
}
