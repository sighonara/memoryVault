package org.sightech.memoryvault.auth

import org.springframework.security.core.context.SecurityContextHolder
import java.util.UUID

object CurrentUser {

    val SYSTEM_USER_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")

    fun userId(): UUID {
        val auth = SecurityContextHolder.getContext().authentication
        return if (auth != null && auth.principal is String) {
            try {
                UUID.fromString(auth.principal as String)
            } catch (e: IllegalArgumentException) {
                SYSTEM_USER_ID
            }
        } else {
            SYSTEM_USER_ID
        }
    }
}
