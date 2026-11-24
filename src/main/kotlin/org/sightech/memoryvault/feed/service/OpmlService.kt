package org.sightech.memoryvault.feed.service

import org.sightech.memoryvault.auth.CurrentUser
import org.sightech.memoryvault.feed.entity.Feed
import org.sightech.memoryvault.feed.entity.FeedCategory
import org.sightech.memoryvault.feed.repository.FeedRepository
import org.sightech.memoryvault.security.UnsafeUrlException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.w3c.dom.Element
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

data class ImportResult(
    val feedsAdded: Int,
    val feedsSkipped: Int,
    val categoriesCreated: Int
)

class OpmlTooLargeException(message: String) : RuntimeException(message)

@Service
class OpmlService(
    private val feedRepository: FeedRepository,
    private val feedService: FeedService,
    private val feedCategoryService: FeedCategoryService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        const val MAX_OPML_BYTES = 2 * 1024 * 1024
        const val MAX_OPML_ENTRIES = 1000
    }

    fun exportOpml(): String {
        val userId = CurrentUser.userId()
        val categories = feedCategoryService.listCategories()
        val feeds = feedRepository.findAllActiveByUserId(userId)
        val feedsByCategory = feeds.groupBy { it.category?.id }

        val docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        val doc = docBuilder.newDocument()

        val opml = doc.createElement("opml").also { it.setAttribute("version", "2.0") }
        doc.appendChild(opml)

        val head = doc.createElement("head")
        val title = doc.createElement("title").also { it.textContent = "MemoryVault Feed Subscriptions" }
        head.appendChild(title)
        opml.appendChild(head)

        val body = doc.createElement("body")
        opml.appendChild(body)

        for (category in categories) {
            val categoryFeeds = feedsByCategory[category.id] ?: continue
            if (categoryFeeds.isEmpty()) continue

            val categoryOutline = doc.createElement("outline").also {
                it.setAttribute("text", category.name)
                it.setAttribute("title", category.name)
            }
            for (feed in categoryFeeds) {
                val feedOutline = doc.createElement("outline").also {
                    it.setAttribute("type", "rss")
                    it.setAttribute("text", feed.title ?: feed.url)
                    it.setAttribute("title", feed.title ?: feed.url)
                    it.setAttribute("xmlUrl", feed.url)
                    if (feed.siteUrl != null) it.setAttribute("htmlUrl", feed.siteUrl)
                }
                categoryOutline.appendChild(feedOutline)
            }
            body.appendChild(categoryOutline)
        }

        val transformer = TransformerFactory.newInstance().newTransformer().apply {
            setOutputProperty(OutputKeys.INDENT, "yes")
            setOutputProperty(OutputKeys.ENCODING, "UTF-8")
            setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
        }
        val writer = StringWriter()
        transformer.transform(DOMSource(doc), StreamResult(writer))
        return writer.toString()
    }

    private suspend fun tryAddFeed(url: String, categoryId: java.util.UUID?): Boolean =
        try {
            if (categoryId == null) feedService.addFeed(url) else feedService.addFeed(url, categoryId)
            true
        } catch (e: UnsafeUrlException) {
            log.warn("Skipped OPML entry with unsafe URL: {}", e.message)
            false
        }

    suspend fun importOpml(opmlContent: String): ImportResult {
        val userId = CurrentUser.userId()
        if (opmlContent.toByteArray().size > MAX_OPML_BYTES) {
            log.warn("Rejected OPML import for user {}: content exceeds {} bytes", userId, MAX_OPML_BYTES)
            throw OpmlTooLargeException("OPML file is too large (max 2 MiB)")
        }
        val existingFeeds = feedRepository.findAllActiveByUserId(userId).map { it.url.lowercase() }.toMutableSet()
        var entriesSeen = 0

        val dbf = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            isXIncludeAware = false
            isExpandEntityReferences = false
        }
        val docBuilder = dbf.newDocumentBuilder()
        val doc = try {
            docBuilder.parse(opmlContent.byteInputStream())
        } catch (e: Exception) {
            log.warn("Failed to parse OPML content: {}", e.message)
            return ImportResult(0, 0, 0)
        }
        val body = doc.getElementsByTagName("body").item(0) as? Element
            ?: return ImportResult(0, 0, 0)

        var feedsAdded = 0
        var feedsSkipped = 0
        var categoriesCreated = 0

        val outlines = body.childNodes
        for (i in 0 until outlines.length) {
            val node = outlines.item(i)
            if (node !is Element || node.tagName != "outline") continue

            val xmlUrl = node.getAttribute("xmlUrl")
            if (xmlUrl.isNotBlank()) {
                // Top-level feed (no category wrapper)
                if (++entriesSeen > MAX_OPML_ENTRIES) break
                if (xmlUrl.lowercase() in existingFeeds) {
                    feedsSkipped++
                } else if (tryAddFeed(xmlUrl, null)) {
                    existingFeeds.add(xmlUrl.lowercase())
                    feedsAdded++
                } else {
                    feedsSkipped++
                }
            } else {
                // Category folder — child outlines are feeds
                val categoryName = node.getAttribute("text").ifBlank { node.getAttribute("title") }
                if (categoryName.isBlank()) continue

                var categoryObj: FeedCategory? = null
                val children = node.childNodes
                for (j in 0 until children.length) {
                    val child = children.item(j)
                    if (child !is Element || child.tagName != "outline") continue
                    val childXmlUrl = child.getAttribute("xmlUrl")
                    if (childXmlUrl.isBlank()) continue
                    if (++entriesSeen > MAX_OPML_ENTRIES) break

                    if (childXmlUrl.lowercase() in existingFeeds) {
                        feedsSkipped++
                        continue
                    }

                    // Lazily resolve/create category only if we have feeds to add
                    if (categoryObj == null) {
                        val (category, wasCreated) = feedCategoryService.findOrCreateByName(categoryName)
                        if (wasCreated) categoriesCreated++
                        categoryObj = category
                    }

                    if (tryAddFeed(childXmlUrl, categoryObj.id)) {
                        existingFeeds.add(childXmlUrl.lowercase())
                        feedsAdded++
                    } else {
                        feedsSkipped++
                    }
                }
            }
            if (entriesSeen > MAX_OPML_ENTRIES) {
                log.warn("OPML import for user {} truncated at {} entries", userId, MAX_OPML_ENTRIES)
                break
            }
        }

        log.info("OPML import for user {}: added={}, skipped={}, categoriesCreated={}", userId, feedsAdded, feedsSkipped, categoriesCreated)
        return ImportResult(feedsAdded, feedsSkipped, categoriesCreated)
    }
}
