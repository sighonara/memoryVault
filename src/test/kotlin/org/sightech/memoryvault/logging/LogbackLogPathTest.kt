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
        assertEquals(LOG_DIR, loggerContext.getProperty("LOG_PATH"))
        assertEquals("$LOG_DIR/memoryvault.log", jsonFileAppender().file)
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
    fun `rolled files go to an archive subdirectory the CloudWatch glob does not match`() {
        val policy = jsonFileAppender().rollingPolicy as TimeBasedRollingPolicy<*>
        assertEquals("$LOG_DIR/archive/memoryvault.%d{yyyy-MM-dd}.log", policy.fileNamePattern)
    }
}
