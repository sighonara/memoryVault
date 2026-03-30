package org.sightech.memoryvault.config

import org.sightech.memoryvault.auth.service.JwtService
import org.slf4j.LoggerFactory
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.messaging.support.MessageBuilder
import org.springframework.stereotype.Component
import java.security.Principal

@Component
class WebSocketAuthInterceptor(private val jwtService: JwtService) : ChannelInterceptor {

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

        val claims = jwtService.validateToken(token)
        if (claims == null) {
            log.warn("WebSocket CONNECT rejected: invalid JWT")
            return null
        }

        val userId = claims["userId"] ?: run {
            log.warn("WebSocket CONNECT rejected: userId claim missing from validated token")
            return null
        }
        accessor.user = Principal { userId }
        accessor.setLeaveMutable(false)
        log.info("WebSocket CONNECT accepted userId={}", userId)

        @Suppress("UNCHECKED_CAST")
        return MessageBuilder.createMessage(message.payload, accessor.messageHeaders)
    }
}
