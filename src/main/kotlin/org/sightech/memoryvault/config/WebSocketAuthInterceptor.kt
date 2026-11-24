package org.sightech.memoryvault.config

import org.slf4j.LoggerFactory
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.MessagingException
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.messaging.support.MessageHeaderAccessor
import org.springframework.stereotype.Component

@Component
class WebSocketAuthInterceptor(private val tokenValidator: StompTokenValidator) : ChannelInterceptor {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun preSend(message: Message<*>, channel: MessageChannel): Message<*>? {
        val accessor = StompHeaderAccessor.wrap(message)

        when (accessor.command) {
            StompCommand.CONNECT -> Unit
            StompCommand.SUBSCRIBE -> {
                authorizeSubscribe(accessor)
                return message
            }
            StompCommand.SEND -> {
                val destination = accessor.destination
                if (destination == null || !destination.startsWith("/app/")) {
                    reject(accessor, "SEND to this destination is not allowed")
                }
                return message
            }
            else -> return message
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

        // The protocol handler registers the session user through the original mutable accessor,
        // so the user must be set on it (not on a copy) for later frames to carry it.
        val original = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor::class.java)
        if (original == null) {
            log.warn("WebSocket CONNECT rejected: message headers are not mutable")
            return null
        }
        original.user = principal
        log.info("WebSocket CONNECT accepted userId={}", principal.name)
        return message
    }

    private fun authorizeSubscribe(accessor: StompHeaderAccessor) {
        val userName = accessor.user?.name
        if (userName == null) {
            reject(accessor, "SUBSCRIBE without authenticated user")
        }
        val destination = accessor.destination
        val allowed = Regex("^/topic/user/" + Regex.escape(userName) + "/[a-z]+$")
        if (destination == null || !allowed.matches(destination)) {
            reject(accessor, "SUBSCRIBE to a destination the user does not own")
        }
    }

    private fun reject(accessor: StompHeaderAccessor, reason: String): Nothing {
        log.warn(
            "WebSocket frame rejected: {} sessionId={} destination={}",
            reason, accessor.sessionId, accessor.destination
        )
        throw MessagingException(reason)
    }
}
