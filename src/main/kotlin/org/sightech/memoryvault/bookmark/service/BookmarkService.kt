package org.sightech.memoryvault.bookmark.service

import org.sightech.memoryvault.bookmark.entity.Bookmark
import org.sightech.memoryvault.bookmark.repository.BookmarkRepository
import org.sightech.memoryvault.tag.service.TagService
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class BookmarkService(
    private val bookmarkRepository: BookmarkRepository,
    private val tagService: TagService
) {

    companion object {
        val SYSTEM_USER_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    }

    fun create(url: String, title: String?, tagNames: List<String>?): Bookmark {
        val bookmark = Bookmark(
            userId = SYSTEM_USER_ID,
            url = url,
            title = title ?: url
        )
        if (!tagNames.isNullOrEmpty()) {
            val tags = tagService.findOrCreateByNames(tagNames)
            bookmark.tags.addAll(tags)
        }
        return bookmarkRepository.save(bookmark)
    }

    fun findAll(query: String?, tagNames: List<String>?): List<Bookmark> {
        var bookmarks = bookmarkRepository.findAllActiveByUserId(SYSTEM_USER_ID)

        if (!query.isNullOrBlank()) {
            val q = query.lowercase()
            bookmarks = bookmarks.filter {
                it.title.lowercase().contains(q) || it.url.lowercase().contains(q)
            }
        }

        if (!tagNames.isNullOrEmpty()) {
            bookmarks = bookmarks.filter { bookmark ->
                val bookmarkTagNames = bookmark.tags.map { it.name }.toSet()
                tagNames.any { it in bookmarkTagNames }
            }
        }

        return bookmarks
    }

    fun updateTags(bookmarkId: UUID, tagNames: List<String>): Bookmark? {
        val bookmark = bookmarkRepository.findActiveById(bookmarkId) ?: return null
        val tags = tagService.findOrCreateByNames(tagNames)
        bookmark.tags.clear()
        bookmark.tags.addAll(tags)
        bookmark.updatedAt = Instant.now()
        return bookmarkRepository.save(bookmark)
    }

    fun softDelete(bookmarkId: UUID): Bookmark? {
        val bookmark = bookmarkRepository.findActiveById(bookmarkId) ?: return null
        bookmark.deletedAt = Instant.now()
        bookmark.updatedAt = Instant.now()
        return bookmarkRepository.save(bookmark)
    }

    fun exportNetscapeHtml(): String {
        val bookmarks = bookmarkRepository.findAllActiveByUserId(SYSTEM_USER_ID)
        val sb = StringBuilder()
        sb.appendLine("<!DOCTYPE NETSCAPE-Bookmark-file-1>")
        sb.appendLine("<!-- This is an automatically generated file. -->")
        sb.appendLine("<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">")
        sb.appendLine("<TITLE>Bookmarks</TITLE>")
        sb.appendLine("<H1>Bookmarks</H1>")
        sb.appendLine("<DL><p>")
        for (bookmark in bookmarks) {
            val addDate = bookmark.createdAt.epochSecond
            val tagAttr = if (bookmark.tags.isNotEmpty()) {
                " TAGS=\"${bookmark.tags.joinToString(",") { it.name }}\""
            } else ""
            sb.appendLine("    <DT><A HREF=\"${bookmark.url}\" ADD_DATE=\"$addDate\"$tagAttr>${bookmark.title}</A>")
        }
        sb.appendLine("</DL><p>")
        return sb.toString()
    }
}
