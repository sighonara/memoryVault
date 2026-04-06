package org.sightech.memoryvault.storage

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import software.amazon.awssdk.core.ResponseInputStream
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.*
import java.io.ByteArrayInputStream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class S3StorageServiceTest {

    private val s3Client = mockk<S3Client>(relaxed = true)
    private lateinit var service: S3StorageService

    @BeforeEach
    fun setUp() {
        service = S3StorageService(s3Client, "test-bucket")
    }

    @Test
    fun `store uploads object and returns S3 key`() {
        val input = ByteArrayInputStream("hello".toByteArray())
        val result = service.store("videos/abc.mp4", input)
        assertEquals("videos/abc.mp4", result)

        val putSlot = slot<PutObjectRequest>()
        verify { s3Client.putObject(capture(putSlot), any<RequestBody>()) }
        assertEquals("test-bucket", putSlot.captured.bucket())
        assertEquals("videos/abc.mp4", putSlot.captured.key())
    }

    @Test
    fun `retrieve returns input stream from S3`() {
        val responseStream = mockk<ResponseInputStream<GetObjectResponse>>(relaxed = true)
        every { s3Client.getObject(any<GetObjectRequest>()) } returns responseStream

        val result = service.retrieve("videos/abc.mp4")
        assertEquals(responseStream, result)
    }

    @Test
    fun `delete removes object from S3`() {
        service.delete("videos/abc.mp4")

        val deleteSlot = slot<DeleteObjectRequest>()
        verify { s3Client.deleteObject(capture(deleteSlot)) }
        assertEquals("test-bucket", deleteSlot.captured.bucket())
        assertEquals("videos/abc.mp4", deleteSlot.captured.key())
    }

    @Test
    fun `exists returns true when head succeeds`() {
        every { s3Client.headObject(any<HeadObjectRequest>()) } returns mockk()
        assertTrue(service.exists("videos/abc.mp4"))
    }

    @Test
    fun `exists returns false when NoSuchKeyException`() {
        every { s3Client.headObject(any<HeadObjectRequest>()) } throws
            NoSuchKeyException.builder().message("not found").build()
        assertFalse(service.exists("videos/abc.mp4"))
    }

    @Test
    fun `usedBytes sums object sizes across pages`() {
        val obj1 = S3Object.builder().size(100L).build()
        val obj2 = S3Object.builder().size(200L).build()
        val response = ListObjectsV2Response.builder()
            .contents(listOf(obj1, obj2))
            .isTruncated(false)
            .build()
        every { s3Client.listObjectsV2(any<ListObjectsV2Request>()) } returns response

        assertEquals(300L, service.usedBytes())
    }
}
