package org.sightech.memoryvault.mcp

import org.slf4j.LoggerFactory
import org.springframework.ai.tool.annotation.Tool
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class SystemTools {

    private val log = LoggerFactory.getLogger(javaClass)

    @Tool(description = "Ping the MemoryVault server to verify it is running. Returns 'pong' with a timestamp.")
    fun ping(): String = "pong at ${Instant.now()}"

    @Tool(description = "Get MemoryVault application name and version.")
    fun getInfo(): String = "memoryVault v0.0.1"
}
