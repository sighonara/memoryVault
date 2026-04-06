package org.sightech.memoryvault.storage

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.*
import java.io.InputStream

@Component
@Profile("aws")
class S3StorageService(
    private val s3Client: S3Client,
    @Value("\${memoryvault.storage.s3-bucket}")
    private val bucket: String
) : StorageService {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun store(key: String, inputStream: InputStream): String {
        val bytes = inputStream.readAllBytes()
        s3Client.putObject(
            PutObjectRequest.builder().bucket(bucket).key(key).build(),
            RequestBody.fromBytes(bytes)
        )
        log.info("Stored object: s3://{}/{} ({} bytes)", bucket, key, bytes.size)
        return key
    }

    override fun retrieve(key: String): InputStream {
        return s3Client.getObject(
            GetObjectRequest.builder().bucket(bucket).key(key).build()
        )
    }

    override fun delete(key: String) {
        s3Client.deleteObject(
            DeleteObjectRequest.builder().bucket(bucket).key(key).build()
        )
        log.info("Deleted object: s3://{}/{}", bucket, key)
    }

    override fun exists(key: String): Boolean {
        return try {
            s3Client.headObject(
                HeadObjectRequest.builder().bucket(bucket).key(key).build()
            )
            true
        } catch (e: NoSuchKeyException) {
            false
        }
    }

    override fun usedBytes(): Long {
        var totalSize = 0L
        var continuationToken: String? = null
        do {
            val request = ListObjectsV2Request.builder()
                .bucket(bucket)
                .apply { continuationToken?.let { continuationToken(it) } }
                .build()
            val response = s3Client.listObjectsV2(request)
            totalSize += response.contents().sumOf { it.size() }
            continuationToken = if (response.isTruncated()) response.nextContinuationToken() else null
        } while (continuationToken != null)
        return totalSize
    }
}
