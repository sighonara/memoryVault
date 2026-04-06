package org.sightech.memoryvault.logging

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient
import software.amazon.awssdk.services.cloudwatchlogs.model.*
import java.time.Instant

@Component
@Profile("aws")
class CloudWatchLogService(
    private val cwClient: CloudWatchLogsClient,
    @Value("\${memoryvault.logging.cloudwatch-log-group}")
    private val logGroupName: String
) : LogService {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun getLogs(level: String?, logger: String?, limit: Int?): List<LogEntry> {
        val effectiveLimit = limit ?: 50
        val query = buildQuery(level, logger, effectiveLimit)

        val now = Instant.now()
        val startQueryResponse = cwClient.startQuery(
            StartQueryRequest.builder()
                .logGroupName(logGroupName)
                .startTime(now.minusSeconds(86400).epochSecond)
                .endTime(now.epochSecond)
                .queryString(query)
                .build()
        )

        return pollForResults(startQueryResponse.queryId())
    }

    private fun buildQuery(level: String?, logger: String?, limit: Int): String {
        val filters = mutableListOf<String>()
        if (level != null) filters.add("level = '$level'")
        if (logger != null) filters.add("logger like /$logger/")

        val filterClause = if (filters.isNotEmpty()) "| filter ${filters.joinToString(" and ")}" else ""
        return "fields @timestamp, level, logger, message, thread $filterClause | sort @timestamp desc | limit $limit"
    }

    private fun pollForResults(queryId: String): List<LogEntry> {
        var attempts = 0
        while (attempts < 30) {
            val response = cwClient.getQueryResults(
                GetQueryResultsRequest.builder().queryId(queryId).build()
            )
            when (response.status()) {
                QueryStatus.COMPLETE -> return parseResults(response.results())
                QueryStatus.FAILED, QueryStatus.CANCELLED -> {
                    log.warn("CloudWatch query {} ended with status {}", queryId, response.status())
                    return emptyList()
                }
                else -> {
                    attempts++
                    Thread.sleep(200)
                }
            }
        }
        log.warn("CloudWatch query {} timed out after 30 attempts", queryId)
        return emptyList()
    }

    private fun parseResults(results: List<List<ResultField>>): List<LogEntry> {
        return results.mapNotNull { row ->
            try {
                val fields = row.associate { it.field() to it.value() }
                LogEntry(
                    timestamp = Instant.parse(fields["@timestamp"]!!),
                    level = fields["level"] ?: "UNKNOWN",
                    logger = fields["logger"] ?: "",
                    message = fields["message"] ?: "",
                    thread = fields["thread"] ?: ""
                )
            } catch (e: Exception) {
                log.debug("Failed to parse CloudWatch log row: {}", e.message)
                null
            }
        }
    }
}
