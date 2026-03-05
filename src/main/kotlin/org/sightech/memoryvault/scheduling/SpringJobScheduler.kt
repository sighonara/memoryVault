package org.sightech.memoryvault.scheduling

import org.slf4j.LoggerFactory
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.support.CronTrigger
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledFuture

@Component
class SpringJobScheduler(private val taskScheduler: TaskScheduler) : JobScheduler {

    private val logger = LoggerFactory.getLogger(SpringJobScheduler::class.java)
    private val jobs = ConcurrentHashMap<String, () -> Unit>()
    private val futures = ConcurrentHashMap<String, ScheduledFuture<*>>()

    override fun schedule(jobName: String, cron: String, task: () -> Unit) {
        jobs[jobName] = task
        if (cron != "-") {
            val future = taskScheduler.schedule(task, CronTrigger(cron))
            if (future != null) {
                futures[jobName] = future
                logger.info("Scheduled job '{}' with cron '{}'", jobName, cron)
            }
        } else {
            logger.info("Job '{}' registered but not scheduled (cron disabled)", jobName)
        }
    }

    override fun triggerNow(jobName: String) {
        val task = jobs[jobName]
        if (task != null) {
            logger.info("Triggering job '{}' immediately", jobName)
            task()
        } else {
            logger.warn("Job '{}' not found", jobName)
        }
    }
}
