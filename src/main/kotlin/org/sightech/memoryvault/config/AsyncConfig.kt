package org.sightech.memoryvault.config

import org.slf4j.LoggerFactory
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.AsyncConfigurer
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

@Configuration
@EnableAsync
class AsyncConfig(
    @Value("\${memoryvault.websocket.relay-executor-pool-size}") private val relayPoolSize: Int
) : AsyncConfigurer {

    @Bean("websocketRelayExecutor")
    fun websocketRelayExecutor(): ThreadPoolTaskExecutor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = relayPoolSize
        executor.maxPoolSize = relayPoolSize
        executor.setThreadNamePrefix("ws-relay-")
        executor.initialize()
        return executor
    }

    // @Async void methods silently swallow exceptions unless a handler is registered.
    // Log every uncaught async exception at ERROR with enough context to trace the call site.
    override fun getAsyncUncaughtExceptionHandler(): AsyncUncaughtExceptionHandler {
        return AsyncUncaughtExceptionHandler { ex, method, params ->
            val log = LoggerFactory.getLogger(method.declaringClass)
            log.error(
                "Uncaught exception in @Async {}({}): {}",
                method.name, params.contentToString(), ex.message, ex
            )
        }
    }
}
