package org.sightech.memoryvault.config

import org.slf4j.LoggerFactory
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.messaging.support.MessageBuilder
import org.springframework.stereotype.Component

@Component
class WebSocketAuthInterceptor(private val tokenValidator: StompTokenValidator) : ChannelInterceptor {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun preSend(message: Message<*>, channel: MessageChannel): Message<*>? {
        val accessor = StompHeaderAccessor.wrap(message)

        if (accessor.command != StompCommand.CONNECT) {
            return message
        }

        val authHeader = accessor.getFirstNativeHeader("Authorization")
        val token = authHeader?.removePrefix("Bearer ")?.trim()

        if (token.isNullOrBlank()) {
            log.warn("WebSocket CONNECT rejected: missing Authorization header")
            return null
        }

        val principal = tokenValidator.validate(token)
        if (principal == null) {
            log.warn("WebSocket CONNECT rejected: token validation failed")
            return null
        }

        accessor.user = principal
        accessor.setLeaveMutable(false)
        log.info("WebSocket CONNECT accepted userId={}", principal.name)

        @Suppress("UNCHECKED_CAST")
        return MessageBuilder.createMessage(message.payload, accessor.messageHeaders)
    }
}
