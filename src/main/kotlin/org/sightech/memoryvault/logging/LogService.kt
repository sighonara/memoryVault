package org.sightech.memoryvault.logging

import java.time.Instant

data class LogEntry(
    val timestamp: Instant,
    val level: String,
    val logger: String,
    val message: String,
    val thread: String
)

interface LogService {
    fun getLogs(level: String?, logger: String?, limit: Int?): List<LogEntry>
}
