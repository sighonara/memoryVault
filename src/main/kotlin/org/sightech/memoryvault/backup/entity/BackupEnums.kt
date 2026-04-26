package org.sightech.memoryvault.backup.entity

enum class BackupProviderType { INTERNET_ARCHIVE, CUSTOM }

enum class BackupStatus { PENDING, UPLOADING, BACKED_UP, LOST, FAILED }
