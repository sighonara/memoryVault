package org.sightech.memoryvault

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class MemoryVaultApplication

fun main(args: Array<String>) {
    runApplication<MemoryVaultApplication>(*args)
}
