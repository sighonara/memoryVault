package org.sightech.memoryvault.config

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.messaging.Message
import org.springframework.messaging.MessagingException
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.MessageBuilder
import java.security.Principal
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class WebSocketAuthInterceptorTest {

    private val tokenValidator = mockk<StompTokenValidator>()
    private val interceptor = WebSocketAuthInterceptor(tokenValidator)
    private val channel = mockk<MessageChannel>()

    private fun buildConnectMessage(token: String?): Message<ByteArray> {
        val accessor = StompHeaderAccessor.create(StompCommand.CONNECT)
        if (token != null) {
            accessor.setNativeHeader("Authorization", "Bearer $token")
        }
        accessor.setLeaveMutable(true)
        return MessageBuilder.createMessage(ByteArray(0), accessor.messageHeaders)
    }

    @Test
    fun `accepts valid JWT and sets principal`() {
        every { tokenValidator.validate("valid-token") } returns
            Principal { "00000000-0000-0000-0000-000000000001" }

        val message = buildConnectMessage("valid-token")
        val result = interceptor.preSend(message, channel)

        assertNotNull(result)
        val accessor = StompHeaderAccessor.wrap(result)
        assertNotNull(accessor.user)
        assert(accessor.user!!.name == "00000000-0000-0000-0000-000000000001")
    }

    @Test
    fun `rejects invalid JWT`() {
        every { tokenValidator.validate("bad-token") } returns null

        val message = buildConnectMessage("bad-token")
        val result = interceptor.preSend(message, channel)

        assertNull(result)
    }

    @Test
    fun `rejects missing Authorization header`() {
        val message = buildConnectMessage(null)
        val result = interceptor.preSend(message, channel)

        assertNull(result)
    }

    private val ownId = "00000000-0000-0000-0000-000000000001"
    private val otherId = "00000000-0000-0000-0000-000000000002"

    private fun buildFrame(command: StompCommand, destination: String?, userName: String?): Message<ByteArray> {
        val accessor = StompHeaderAccessor.create(command)
        if (destination != null) accessor.destination = destination
        if (userName != null) accessor.user = Principal { userName }
        accessor.setLeaveMutable(true)
        return MessageBuilder.createMessage(ByteArray(0), accessor.messageHeaders)
    }

    @Test
    fun `passes through UNSUBSCRIBE and DISCONNECT messages`() {
        assertNotNull(interceptor.preSend(buildFrame(StompCommand.UNSUBSCRIBE, null, ownId), channel))
        assertNotNull(interceptor.preSend(buildFrame(StompCommand.DISCONNECT, null, ownId), channel))
    }

    @Test
    fun `subscribe to own topic passes`() {
        for (suffix in listOf("feeds", "jobs", "videos", "ingests", "sync", "backups")) {
            val result = interceptor.preSend(buildFrame(StompCommand.SUBSCRIBE, "/topic/user/$ownId/$suffix", ownId), channel)
            assertNotNull(result)
        }
    }

    @Test
    fun `subscribe to another user's topic is rejected`() {
        assertThrows<MessagingException> {
            interceptor.preSend(buildFrame(StompCommand.SUBSCRIBE, "/topic/user/$otherId/sync", ownId), channel)
        }
    }

    @Test
    fun `wildcard subscribe is rejected`() {
        assertThrows<MessagingException> {
            interceptor.preSend(buildFrame(StompCommand.SUBSCRIBE, "/topic/user/*/sync", ownId), channel)
        }
        assertThrows<MessagingException> {
            interceptor.preSend(buildFrame(StompCommand.SUBSCRIBE, "/topic/user/**", ownId), channel)
        }
    }

    @Test
    fun `subscribe without user is rejected`() {
        assertThrows<MessagingException> {
            interceptor.preSend(buildFrame(StompCommand.SUBSCRIBE, "/topic/user/$ownId/sync", null), channel)
        }
    }

    @Test
    fun `send to topic is rejected`() {
        assertThrows<MessagingException> {
            interceptor.preSend(buildFrame(StompCommand.SEND, "/topic/user/$otherId/sync", ownId), channel)
        }
    }
}
