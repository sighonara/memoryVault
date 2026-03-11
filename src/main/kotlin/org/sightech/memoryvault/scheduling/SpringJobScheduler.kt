package org.sightech.memoryvault.scheduling

import org.sightech.memoryvault.auth.CurrentUser
import org.sightech.memoryvault.scheduling.entity.JobType
import org.sightech.memoryvault.scheduling.entity.TriggerSource
import org.sightech.memoryvault.scheduling.service.SyncJobService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.support.CronTrigger
import org.springframework.stereotype.Component
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledFuture

@Component
class SpringJobScheduler(
    private val taskScheduler: TaskScheduler,
    private val syncJobService: SyncJobService
) : JobScheduler {

    private val logger = LoggerFactory.getLogger(SpringJobScheduler::class.java)
    private val jobs = ConcurrentHashMap<String, JobRegistration>()
    private val futures = ConcurrentHashMap<String, ScheduledFuture<*>>()

    private data class JobRegistration(val jobType: JobType, val task: () -> Map<String, Any>?)

    override fun schedule(jobName: String, cron: String, jobType: JobType, task: () -> Map<String, Any>?) {
        jobs[jobName] = JobRegistration(jobType, task)
        if (cron != "-") {
            val wrappedTask = Runnable { executeWithTracking(jobName, TriggerSource.SCHEDULED) }
            val future = taskScheduler.schedule(wrappedTask, CronTrigger(cron))
            if (future != null) {
                futures[jobName] = future
                logger.info("Scheduled job '{}' with cron '{}'", jobName, cron)
            }
        } else {
            logger.info("Job '{}' registered but not scheduled (cron disabled)", jobName)
        }
    }

    override fun triggerNow(jobName: String) {
        if (jobs.containsKey(jobName)) {
            logger.info("Triggering job '{}' immediately", jobName)
            executeWithTracking(jobName, TriggerSource.MANUAL)
        } else {
            logger.warn("Job '{}' not found", jobName)
        }
    }

    private fun executeWithTracking(jobName: String, triggeredBy: TriggerSource) {
        val registration = jobs[jobName] ?: return
        val syncJob = syncJobService.recordStart(registration.jobType, triggeredBy, CurrentUser.SYSTEM_USER_ID)

        try {
            val metadata = registration.task()
            syncJobService.recordSuccess(syncJob.id, metadata)
        } catch (e: Exception) {
            logger.error("Job '{}' failed: {}", jobName, e.message, e)
            syncJobService.recordFailure(syncJob.id, e.message ?: "Unknown error")
        }
    }
}
