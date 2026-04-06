package org.sightech.memoryvault.config

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class AwsConfigTest {

    @Test
    fun `creates S3Client with specified region`() {
        val config = AwsConfig()
        config.s3Region = "us-east-1"
        assertDoesNotThrow { config.s3Client() }
    }

    @Test
    fun `creates CloudWatchLogsClient with specified region`() {
        val config = AwsConfig()
        config.cloudwatchRegion = "us-east-1"
        assertDoesNotThrow { config.cloudWatchLogsClient() }
    }
}
