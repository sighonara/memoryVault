package org.sightech.memoryvault.graphql

import org.sightech.memoryvault.auth.CurrentUser
import org.sightech.memoryvault.logging.LogEntry
import org.sightech.memoryvault.logging.LogService
import org.sightech.memoryvault.scheduling.entity.JobType
import org.sightech.memoryvault.scheduling.entity.SyncJob
import org.sightech.memoryvault.scheduling.service.SyncJobService
import org.sightech.memoryvault.search.ContentType
import org.sightech.memoryvault.search.SearchResult
import org.sightech.memoryvault.search.SearchService
import org.sightech.memoryvault.stats.StatsService
import org.sightech.memoryvault.stats.SystemStats
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.stereotype.Controller
import java.util.UUID

@Controller
class AdminResolver(
    private val syncJobService: SyncJobService,
    private val logService: LogService,
    private val statsService: StatsService,
    private val searchService: SearchService
) {

    @QueryMapping
    fun jobs(@Argument type: String?, @Argument limit: Int?): List<SyncJob> {
        val userId = CurrentUser.userId()
        val jobType = type?.let { JobType.valueOf(it) }
        return syncJobService.listJobs(userId, jobType, limit ?: 20)
    }

    @QueryMapping
    fun logs(@Argument level: String?, @Argument service: String?, @Argument limit: Int?): List<LogEntry> {
        return logService.getLogs(level, service, limit ?: 50)
    }

    @QueryMapping
    fun stats(): SystemStats {
        val userId = CurrentUser.userId()
        return statsService.getStats(userId)
    }

    @QueryMapping
    fun search(@Argument query: String, @Argument types: List<String>?): List<SearchResult> {
        val userId = CurrentUser.userId()
        val contentTypes = types?.map { ContentType.valueOf(it) }
        return searchService.search(query, contentTypes, userId, 50)
    }
}
