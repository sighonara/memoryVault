package org.sightech.memoryvault.logging

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

@Component
@Profile("!aws")
class LocalLogService(
    @Value("\${memoryvault.logging.path:\${user.home}/.memoryvault/logs}/memoryvault.log")
    private val logFilePath: String
) : LogService {

    private val objectMapper = ObjectMapper()

    override fun getLogs(level: String?, logger: String?, limit: Int?): List<LogEntry> {
        val path = Path.of(logFilePath)
        if (!Files.exists(path)) return emptyList()

        val effectiveLimit = limit ?: 50

        return Files.readAllLines(path)
            .asReversed()
            .asSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { line -> parseLine(line) }
            .filter { entry ->
                (level == null || entry.level.equals(level, ignoreCase = true)) &&
                    (logger == null || entry.logger.contains(logger, ignoreCase = true))
            }
            .take(effectiveLimit)
            .toList()
    }

    private fun parseLine(line: String): LogEntry? {
        return try {
            val node = objectMapper.readTree(line)
            LogEntry(
                timestamp = Instant.parse(node.get("timestamp").asText()),
                level = node.get("level").asText(),
                logger = node.get("logger").asText(),
                message = node.get("message").asText(),
                thread = node.get("thread").asText()
            )
        } catch (_: Exception) {
            null
        }
    }
}
