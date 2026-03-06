package org.sightech.memoryvault.logging

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

// AWS CloudWatch Logs implementation of LogService.
//
// When activated (spring.profiles.active=aws), this replaces LocalLogService.
//
// Implementation notes for Phase 6:
// - Use AWS SDK v2 CloudWatchLogsClient (software.amazon.awssdk:cloudwatchlogs)
// - Configure via properties:
//     memoryvault.logging.cloudwatch-log-group=/memoryvault/application
//     memoryvault.logging.cloudwatch-region=us-east-1
//
// - getLogs() should use CloudWatch Logs Insights:
//     CloudWatchLogsClient.startQuery(StartQueryRequest.builder()
//         .logGroupName(logGroupName)
//         .startTime(...)
//         .endTime(...)
//         .queryString("fields @timestamp, @message | sort @timestamp desc | limit $limit")
//         .build())
//
// - Then poll with getQueryResults() until status is COMPLETE
// - Parse results into LogEntry objects
// - AWS creates its own logs from the application's stdout/stderr — this service
//   retrieves those logs back for viewing via MCP tool

@Component
@Profile("aws")
class CloudWatchLogService : LogService {

    private val logger = LoggerFactory.getLogger(CloudWatchLogService::class.java)

    override fun getLogs(level: String?, logger: String?, limit: Int?): List<LogEntry> {
        this.logger.warn("CloudWatchLogService.getLogs() is a stub — AWS implementation pending (Phase 6)")
        return emptyList()
    }
}
