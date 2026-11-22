package org.sightech.memoryvault.config

import org.slf4j.LoggerFactory
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.MessagingException
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.messaging.support.MessageBuilder
import org.springframework.stereotype.Component
import java.security.Principal

@Component
class WebSocketAuthInterceptor(private val tokenValidator: StompTokenValidator) : ChannelInterceptor {

    private val log = LoggerFactory.getLogger(javaClass)

    private companion object {
        const val PRINCIPAL_ATTRIBUTE = "memoryvault.stomp.principal"
    }

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
                if (destination != null && destination.startsWith("/topic")) {
                    reject(accessor, "SEND to broker topic is not allowed")
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

        accessor.user = principal
        // Later frames of this session do not carry the CONNECT user header, so remember it per session.
        accessor.sessionAttributes?.put(PRINCIPAL_ATTRIBUTE, principal)
        accessor.setLeaveMutable(false)
        log.info("WebSocket CONNECT accepted userId={}", principal.name)

        @Suppress("UNCHECKED_CAST")
        return MessageBuilder.createMessage(message.payload, accessor.messageHeaders)
    }

    private fun authorizeSubscribe(accessor: StompHeaderAccessor) {
        val sessionPrincipal = accessor.sessionAttributes?.get(PRINCIPAL_ATTRIBUTE) as? Principal
        val userName = (accessor.user ?: sessionPrincipal)?.name
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
