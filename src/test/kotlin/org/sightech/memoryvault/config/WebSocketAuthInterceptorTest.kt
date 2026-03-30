package org.sightech.memoryvault.config

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.auth.service.JwtService
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.MessageBuilder
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class WebSocketAuthInterceptorTest {

    private val jwtService = mockk<JwtService>()
    private val interceptor = WebSocketAuthInterceptor(jwtService)
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
        every { jwtService.validateToken("valid-token") } returns mapOf(
            "userId" to "00000000-0000-0000-0000-000000000001",
            "email" to "test@test.com",
            "role" to "OWNER"
        )

        val message = buildConnectMessage("valid-token")
        val result = interceptor.preSend(message, channel)

        assertNotNull(result)
        val accessor = StompHeaderAccessor.wrap(result)
        assertNotNull(accessor.user)
        assert(accessor.user!!.name == "00000000-0000-0000-0000-000000000001")
    }

    @Test
    fun `rejects invalid JWT`() {
        every { jwtService.validateToken("bad-token") } returns null

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

    @Test
    fun `passes through non-CONNECT messages`() {
        val accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE)
        accessor.setLeaveMutable(true)
        val message = MessageBuilder.createMessage(ByteArray(0), accessor.messageHeaders)

        val result = interceptor.preSend(message, channel)

        assertNotNull(result)
    }
}
