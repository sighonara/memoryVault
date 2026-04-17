package org.sightech.memoryvault.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.core.task.SyncTaskExecutor
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor
import java.util.concurrent.ThreadPoolExecutor

@Configuration
class VideoDownloadAsyncConfig {

    // Two concurrent downloads keeps CPU/network sensible on a t-class EC2 and avoids
    // saturating disk with in-flight temp files. CallerRunsPolicy is deliberate: when the
    // queue is full the submitting thread runs the work itself, back-pressuring the caller
    // (Lambda-triggered sync) rather than silently dropping downloads or surfacing a 500.
    @Bean(name = ["videoDownloadExecutor"])
    @Profile("!test")
    fun videoDownloadExecutor(): Executor = ThreadPoolTaskExecutor().apply {
        corePoolSize = 2
        maxPoolSize = 2
        queueCapacity = 100
        setThreadNamePrefix("video-dl-")
        setRejectedExecutionHandler(ThreadPoolExecutor.CallerRunsPolicy())
        setWaitForTasksToCompleteOnShutdown(true)
        setAwaitTerminationSeconds(60)
        initialize()
    }

    // Under the test profile the executor runs tasks on the caller thread so that integration
    // tests observe @Async downloads as fully completed before assertions run.
    @Bean(name = ["videoDownloadExecutor"])
    @Profile("test")
    fun videoDownloadExecutorForTests(): Executor = SyncTaskExecutor()
}
