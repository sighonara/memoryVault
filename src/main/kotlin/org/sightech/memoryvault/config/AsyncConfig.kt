package org.sightech.memoryvault.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

@Configuration
@EnableAsync
class AsyncConfig(
    @Value("\${memoryvault.websocket.relay-executor-pool-size}") private val relayPoolSize: Int
) {

    @Bean("websocketRelayExecutor")
    fun websocketRelayExecutor(): ThreadPoolTaskExecutor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = relayPoolSize
        executor.maxPoolSize = relayPoolSize
        executor.setThreadNamePrefix("ws-relay-")
        executor.initialize()
        return executor
    }
}
