package org.sightech.memoryvault.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient
import software.amazon.awssdk.services.costexplorer.CostExplorerClient
import software.amazon.awssdk.services.s3.S3Client

@Configuration
@Profile("aws")
class AwsConfig {

    @Value("\${memoryvault.storage.s3-region:us-east-1}")
    lateinit var s3Region: String

    @Value("\${memoryvault.logging.cloudwatch-region:us-east-1}")
    lateinit var cloudwatchRegion: String

    @Bean
    fun s3Client(): S3Client = S3Client.builder()
        .region(Region.of(s3Region))
        .build()

    @Bean
    fun cloudWatchLogsClient(): CloudWatchLogsClient = CloudWatchLogsClient.builder()
        .region(Region.of(cloudwatchRegion))
        .build()

    @Bean
    fun costExplorerClient(): CostExplorerClient = CostExplorerClient.builder()
        .region(Region.US_EAST_1)
        .build()
}
