package org.sightech.memoryvault.youtube.service

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.util.UUID

// AWS Lambda implementation of VideoDownloader.
//
// When activated (spring.profiles.active=aws), this replaces LocalVideoDownloader.
//
// Implementation notes for Phase 6:
// - Use AWS SDK v2 LambdaClient (software.amazon.awssdk:lambda)
// - Configure via properties:
//     memoryvault.youtube.lambda-function-name=memoryvault-video-downloader
//     memoryvault.youtube.s3-bucket=memoryvault-storage
//
// - download() should invoke the Lambda asynchronously (InvocationType.EVENT):
//     LambdaClient.invoke(InvokeRequest.builder()
//         .functionName(lambdaFunctionName)
//         .invocationType(InvocationType.EVENT)
//         .payload(SdkBytes.fromUtf8String(json))
//         .build())
//
// - Lambda payload shape:
//     {
//       "videoId": "<uuid>",
//       "youtubeUrl": "<url>",
//       "s3Bucket": "<bucket>",
//       "s3Key": "videos/<videoId>/<filename>"
//     }
//
// - The Lambda function (Python, content-processor/):
//     1. Invokes yt-dlp to download the video to /tmp
//     2. Uploads to S3 using boto3 multipart upload
//     3. Updates the videos table directly via psycopg2:
//        SET file_path = s3Key, downloaded_at = now(), updated_at = now()
//     4. Alternatively, posts a completion message to an SQS queue
//        that Spring Boot consumes to update the DB
//
// - Lambda timeout: 15 minutes (max). For very long videos, consider
//   ECS Fargate tasks instead of Lambda.
// - Lambda memory: 1024MB minimum (yt-dlp + ffmpeg need RAM)
// - Lambda layers: yt-dlp and ffmpeg as Lambda layers or bundled in container image

@Component
@Profile("aws")
class LambdaVideoDownloader : VideoDownloader {

    private val logger = LoggerFactory.getLogger(LambdaVideoDownloader::class.java)

    override fun download(youtubeUrl: String, videoId: UUID): DownloadResult {
        logger.warn("LambdaVideoDownloader.download() is a stub — AWS Lambda invocation pending (Phase 6)")
        return DownloadResult(
            success = false,
            error = "Lambda video downloader not yet implemented. Use 'local' profile for development."
        )
    }
}
