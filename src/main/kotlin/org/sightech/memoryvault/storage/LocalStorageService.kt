package org.sightech.memoryvault.storage

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

@Component
@Profile("local | test")
class LocalStorageService(
    @Value("\${memoryvault.storage.local-path:\${user.home}/.memoryvault/storage}")
    private val basePath: String
) : StorageService {

    private val logger = LoggerFactory.getLogger(LocalStorageService::class.java)

    private fun resolve(key: String): Path = Path.of(basePath).resolve(key)

    override fun store(key: String, inputStream: InputStream): String {
        val target = resolve(key)
        Files.createDirectories(target.parent)
        Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING)
        logger.info("Stored file: {}", target)
        return target.toString()
    }

    override fun retrieve(key: String): InputStream {
        val target = resolve(key)
        return Files.newInputStream(target)
    }

    override fun delete(key: String) {
        val target = resolve(key)
        Files.deleteIfExists(target)
        logger.info("Deleted file: {}", target)
    }

    override fun exists(key: String): Boolean {
        return Files.exists(resolve(key))
    }

    override fun usedBytes(): Long {
        val base = Path.of(basePath)
        if (!Files.exists(base)) return 0
        return Files.walk(base)
            .filter { Files.isRegularFile(it) }
            .mapToLong { Files.size(it) }
            .sum()
    }
}
