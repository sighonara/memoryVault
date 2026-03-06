package org.sightech.memoryvault.storage

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.io.InputStream

// AWS S3 implementation of StorageService.
//
// When activated (spring.profiles.active=aws), this replaces LocalStorageService.
//
// Implementation notes for Phase 6:
// - Use AWS SDK v2 S3Client (software.amazon.awssdk:s3)
// - Configure via properties:
//     memoryvault.storage.s3-bucket=memoryvault-storage
//     memoryvault.storage.s3-region=us-east-1
// - store(): Use S3Client.putObject() for small files, S3TransferManager for large videos
//   (multipart upload threshold ~100MB). The key becomes the S3 object key.
// - retrieve(): Use S3Client.getObject() to return an InputStream. For web UI access,
//   consider generating pre-signed URLs (S3Presigner) with 1-hour expiry instead.
// - delete(): Use S3Client.deleteObject()
// - exists(): Use S3Client.headObject(), catch NoSuchKeyException
// - Consider S3 lifecycle policies for cost optimization:
//   - Move to Infrequent Access after 90 days
//   - Move to Glacier Deep Archive after 365 days (videos unlikely to be re-watched)
// - Bucket should have versioning enabled for accidental deletion protection

@Component
@Profile("aws")
class S3StorageService : StorageService {

    private val logger = LoggerFactory.getLogger(S3StorageService::class.java)

    override fun store(key: String, inputStream: InputStream): String {
        logger.warn("S3StorageService.store() is a stub — AWS implementation pending (Phase 6)")
        throw UnsupportedOperationException("S3 storage not yet implemented. Use 'local' profile for development.")
    }

    override fun retrieve(key: String): InputStream {
        throw UnsupportedOperationException("S3 storage not yet implemented. Use 'local' profile for development.")
    }

    override fun delete(key: String) {
        throw UnsupportedOperationException("S3 storage not yet implemented. Use 'local' profile for development.")
    }

    override fun exists(key: String): Boolean {
        throw UnsupportedOperationException("S3 storage not yet implemented. Use 'local' profile for development.")
    }

    // TODO: Phase 6 — use CloudWatch GetMetricData for BucketSizeBytes,
    //  or S3 inventory reports for bucket-level size tracking.
    override fun usedBytes(): Long {
        logger.warn("S3StorageService.usedBytes() is a stub — AWS implementation pending (Phase 6)")
        return 0
    }
}
