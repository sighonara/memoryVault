package org.sightech.memoryvault.scheduling

interface JobScheduler {
    fun schedule(jobName: String, cron: String, task: () -> Unit)
    fun triggerNow(jobName: String)
}
