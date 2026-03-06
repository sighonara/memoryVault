package org.sightech.memoryvault.scheduling

interface JobScheduler {
    fun schedule(jobName: String, cron: String, jobType: String, task: () -> Map<String, Any>?)
    fun triggerNow(jobName: String)
}
