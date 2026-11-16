package org.sightech.memoryvault.logging

import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.core.rolling.RollingFileAppender
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.io.File
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * logback-spring.xml must take LOG_PATH from the Spring Environment, not only from JVM
 * system properties. Prod sets `memoryvault.logging.path=/app/logs` in
 * application-prod.properties; when logback could only see system properties, prod logged
 * to the container's home directory and nothing reached the CloudWatch agent.
 *
 * The Gradle test task sets the system property `memoryvault.logging.path=build/test-logs`.
 * This test overrides it with a Spring-level (inlined) property and asserts logback follows
 * the Spring value — which a plain `<property>` would not.
 */
@Testcontainers
@SpringBootTest(properties = ["memoryvault.logging.path=" + LogbackLogPathTest.LOG_DIR])
@ActiveProfiles("test")
class LogbackLogPathTest {

    companion object {
        const val LOG_DIR = "build/test-logs/logback-log-path-test"

        @Container
        val postgres = PostgreSQLContainer("postgres:16-alpine").apply {
            withDatabaseName("memoryvault_test")
            withUsername("memoryvault")
            withPassword("memoryvault")
        }

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }

    private val loggerContext get() = LoggerFactory.getILoggerFactory() as LoggerContext

    private fun jsonFileAppender(): RollingFileAppender<*> =
        loggerContext.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).getAppender("JSON_FILE") as RollingFileAppender<*>

    @Test
    fun `LOG_PATH comes from the Spring environment, overriding the system property`() {
        val systemProperty = System.getProperty("memoryvault.logging.path")
        assertTrue(
            systemProperty != null && systemProperty.endsWith("build/test-logs"),
            "precondition: Gradle sets the system property to .../build/test-logs, was $systemProperty"
        )
        // Asserted via the appender's resolved file path (not the LoggerContext property), so the
        // test doesn't require LOG_PATH to be context-scoped — see the test below for why it isn't.
        assertEquals("$LOG_DIR/memoryvault.log", jsonFileAppender().file)
    }

    @Test
    fun `JSON log lines do not carry logback config properties as fields`() {
        // LogstashEncoder writes every LoggerContext-scoped property into every event, so a
        // context-scoped LOG_PATH showed up as "LOG_PATH":"/app/logs" on every prod log line.
        val marker = "logback-no-config-fields ${UUID.randomUUID()}"
        LoggerFactory.getLogger(javaClass).info(marker)

        val line = File(jsonFileAppender().file).readLines().last { it.contains(marker) }
        assertFalse(line.contains("\"LOG_PATH\""), "LOG_PATH leaked into the JSON event: $line")
    }

    @Test
    fun `log lines are written to the configured file`() {
        val marker = "logback-log-path-test ${UUID.randomUUID()}"
        LoggerFactory.getLogger(javaClass).info(marker)

        val file = File(jsonFileAppender().file)
        assertTrue(file.exists(), "expected ${file.absolutePath} to exist")
        assertTrue(file.readText().contains(marker), "expected the marker line in ${file.absolutePath}")
    }

    @Test
    fun `JSON timestamps are fixed-shape UTC with milliseconds, matching the CloudWatch agent format`() {
        // The agent (user_data.sh) parses "%Y-%m-%dT%H:%M:%S.%f" in UTC. The default ISO output
        // varied (zone offset, 1-9 fractional digits, fraction omitted when zero), so lines
        // could fail to match and lose their real timestamp.
        val marker = "logback-timestamp-shape ${UUID.randomUUID()}"
        LoggerFactory.getLogger(javaClass).info(marker)

        val line = File(jsonFileAppender().file).readLines().last { it.contains(marker) }
        val timestamp = Regex("\"timestamp\":\"([^\"]+)\"").find(line)?.groupValues?.get(1)
        assertTrue(
            timestamp != null && Regex("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z""").matches(timestamp),
            "timestamp must look like 2026-11-15T23:40:16.122Z, was $timestamp"
        )
        // Must stay parseable by LocalLogService / CloudWatchLogService fallback.
        java.time.Instant.parse(timestamp)
    }

    @Test
    fun `rolled files go to an archive subdirectory the CloudWatch glob does not match`() {
        val policy = jsonFileAppender().rollingPolicy as TimeBasedRollingPolicy<*>
        assertEquals("$LOG_DIR/archive/memoryvault.%d{yyyy-MM-dd}.log", policy.fileNamePattern)
    }
}
