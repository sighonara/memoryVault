package org.sightech.memoryvault.feed.service

import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.feed.entity.FeedCategory
import org.sightech.memoryvault.feed.repository.FeedCategoryRepository
import org.sightech.memoryvault.feed.repository.FeedRepository
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class FeedCategoryServiceTest {

    private val categoryRepository = mockk<FeedCategoryRepository>()
    private val feedRepository = mockk<FeedRepository>()
    private val service = FeedCategoryService(categoryRepository, feedRepository)
    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        val securityContext = mockk<SecurityContext>()
        val authentication = mockk<Authentication>()
        every { securityContext.authentication } returns authentication
        every { authentication.principal } returns userId.toString()
        SecurityContextHolder.setContext(securityContext)
    }

    @AfterTest
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `createCategory assigns next sort order`() {
        every { categoryRepository.findMaxSortOrderByUserId(userId) } returns 2
        every { categoryRepository.save(any()) } answers { firstArg() }

        val result = service.createCategory("Tech")

        assertEquals("Tech", result.name)
        assertEquals(3, result.sortOrder)
        verify { categoryRepository.save(any()) }
    }

    @Test
    fun `renameCategory prevents renaming Subscribed`() {
        val subscribed = FeedCategory(userId = userId, name = "Subscribed", sortOrder = 0)
        every { categoryRepository.findActiveByIdAndUserId(subscribed.id, userId) } returns subscribed

        assertFailsWith<IllegalArgumentException> {
            service.renameCategory(subscribed.id, "Something Else")
        }
    }

    @Test
    fun `renameCategory updates name`() {
        val category = FeedCategory(userId = userId, name = "Old Name", sortOrder = 1)
        every { categoryRepository.findActiveByIdAndUserId(category.id, userId) } returns category
        every { categoryRepository.save(any()) } answers { firstArg() }

        val result = service.renameCategory(category.id, "New Name")

        assertNotNull(result)
        assertEquals("New Name", result.name)
    }

    @Test
    fun `deleteCategory prevents deleting Subscribed`() {
        val subscribed = FeedCategory(userId = userId, name = "Subscribed", sortOrder = 0)
        every { categoryRepository.findActiveByIdAndUserId(subscribed.id, userId) } returns subscribed

        assertFailsWith<IllegalArgumentException> {
            service.deleteCategory(subscribed.id)
        }
    }

    @Test
    fun `deleteCategory moves feeds to Subscribed then soft deletes`() {
        val subscribed = FeedCategory(userId = userId, name = "Subscribed", sortOrder = 0)
        val category = FeedCategory(userId = userId, name = "Tech", sortOrder = 1)
        every { categoryRepository.findActiveByIdAndUserId(category.id, userId) } returns category
        every { categoryRepository.findActiveByUserIdAndNameIgnoreCase(userId, "Subscribed") } returns subscribed
        every { feedRepository.moveFeedsBetweenCategories(category.id, subscribed.id, userId, any()) } returns 3
        every { categoryRepository.save(any()) } answers { firstArg() }

        val result = service.deleteCategory(category.id)

        assertTrue(result)
        assertNotNull(category.deletedAt)
        verify { feedRepository.moveFeedsBetweenCategories(category.id, subscribed.id, userId, any()) }
    }

    @Test
    fun `reorderCategories sets sort order by position`() {
        val cat1 = FeedCategory(userId = userId, name = "A", sortOrder = 0)
        val cat2 = FeedCategory(userId = userId, name = "B", sortOrder = 1)
        every { categoryRepository.findAllActiveByUserId(userId) } returns listOf(cat1, cat2)
        every { categoryRepository.saveAll(any<List<FeedCategory>>()) } answers { firstArg() }

        service.reorderCategories(listOf(cat2.id, cat1.id))

        assertEquals(1, cat1.sortOrder)
        assertEquals(0, cat2.sortOrder)
    }
}
