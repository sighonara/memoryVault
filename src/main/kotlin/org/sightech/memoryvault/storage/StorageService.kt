package org.sightech.memoryvault.storage

import java.io.InputStream

interface StorageService {
    fun store(key: String, inputStream: InputStream): String
    fun retrieve(key: String): InputStream
    fun delete(key: String)
    fun exists(key: String): Boolean
    fun usedBytes(): Long
}
