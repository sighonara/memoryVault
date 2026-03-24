package org.sightech.memoryvault.feed.service

import org.sightech.memoryvault.auth.CurrentUser
import org.sightech.memoryvault.feed.entity.Feed
import org.sightech.memoryvault.feed.entity.FeedCategory
import org.sightech.memoryvault.feed.repository.FeedRepository
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

@Service
class OpmlService(
    private val feedRepository: FeedRepository,
    private val feedService: FeedService,
    private val feedCategoryService: FeedCategoryService
) {
    private val log = LoggerFactory.getLogger(javaClass)

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

    suspend fun importOpml(opmlContent: String): ImportResult {
        val userId = CurrentUser.userId()
        val existingFeeds = feedRepository.findAllActiveByUserId(userId).map { it.url.lowercase() }.toSet()

        val docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        val doc = docBuilder.parse(opmlContent.byteInputStream())
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
                if (xmlUrl.lowercase() in existingFeeds) {
                    feedsSkipped++
                } else {
                    feedService.addFeed(xmlUrl)
                    feedsAdded++
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

                    feedService.addFeed(childXmlUrl, categoryObj.id)
                    feedsAdded++
                }
            }
        }

        log.info("OPML import for user {}: added={}, skipped={}, categoriesCreated={}", userId, feedsAdded, feedsSkipped, categoriesCreated)
        return ImportResult(feedsAdded, feedsSkipped, categoriesCreated)
    }
}
