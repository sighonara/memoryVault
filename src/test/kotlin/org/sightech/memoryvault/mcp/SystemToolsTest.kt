package org.sightech.memoryvault.mcp

import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class SystemToolsTest {

    private val tools = SystemTools()

    @Test
    fun `ping returns pong with timestamp`() {
        val result = tools.ping()
        assertTrue(result.startsWith("pong"), "Expected response to start with 'pong', got: $result")
    }

    @Test
    fun `getInfo returns application name and version`() {
        val result = tools.getInfo()
        assertTrue(result.contains("memoryVault"), "Expected result to contain app name")
        assertTrue(result.contains("0.0.1"), "Expected result to contain version")
    }
}
