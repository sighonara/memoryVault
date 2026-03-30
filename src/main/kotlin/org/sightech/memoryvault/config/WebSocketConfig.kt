package org.sightech.memoryvault.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.simp.config.ChannelRegistration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer

@Configuration
@EnableWebSocketMessageBroker
@EnableAsync
class WebSocketConfig(
    private val webSocketAuthInterceptor: WebSocketAuthInterceptor,
    @Value("\${memoryvault.websocket.allowed-origins}") private val allowedOrigins: String,
    @Value("\${memoryvault.websocket.heartbeat-interval-ms}") private val heartbeatInterval: Long,
    @Value("\${memoryvault.websocket.relay-executor-pool-size}") private val relayPoolSize: Int
) : WebSocketMessageBrokerConfigurer {

    override fun configureMessageBroker(registry: MessageBrokerRegistry) {
        registry.enableSimpleBroker("/topic")
            .setHeartbeatValue(longArrayOf(heartbeatInterval, heartbeatInterval))
        registry.setApplicationDestinationPrefixes("/app")
    }

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        registry.addEndpoint("/ws")
            .setAllowedOrigins(*allowedOrigins.split(",").map { it.trim() }.toTypedArray())
            .withSockJS()
    }

    override fun configureClientInboundChannel(registration: ChannelRegistration) {
        registration.interceptors(webSocketAuthInterceptor)
    }

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
