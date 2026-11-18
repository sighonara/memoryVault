package org.sightech.memoryvault.backup

import org.junit.jupiter.api.Test
import org.springframework.scheduling.support.CronExpression
import java.util.Properties
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Phase 10's backup jobs are scheduled in-process by SpringJobScheduler and default to "-"
 * (disabled) in application.properties. Prod shipped without enabling them, so no upload or
 * health check ever ran on AWS (found 2026-11-12). This guards the prod schedule.
 */
class ProdBackupScheduleConfigTest {

    private val prodProperties = Properties().apply {
        val stream = ProdBackupScheduleConfigTest::class.java.classLoader.getResourceAsStream("application-prod.properties")
        assertNotNull(stream, "application-prod.properties must be on the classpath")
        stream.use { load(it) }
    }

    private fun assertEnabledCron(key: String, expected: String) {
        val value = prodProperties.getProperty(key)
        assertNotNull(value, "$key must be set in application-prod.properties")
        assertTrue(value != "-", "$key must not be disabled (\"-\") in prod")
        CronExpression.parse(value) // throws if not a valid Spring cron expression
        assertEquals(expected, value, "$key should match the Phase 10 design default")
    }

    @Test
    fun `backup upload job is enabled in prod every 6 hours`() {
        assertEnabledCron("memoryvault.backup.upload-cron", "0 0 */6 * * *")
    }

    @Test
    fun `backup health check job is enabled in prod daily`() {
        assertEnabledCron("memoryvault.backup.health-check-cron", "0 0 6 * * *")
    }
}
