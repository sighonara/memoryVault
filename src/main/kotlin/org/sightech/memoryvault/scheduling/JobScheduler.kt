package org.sightech.memoryvault.scheduling

import org.sightech.memoryvault.scheduling.entity.JobType

interface JobScheduler {
    fun schedule(jobName: String, cron: String, jobType: JobType, task: () -> Map<String, Any>?)
    fun triggerNow(jobName: String)
}
