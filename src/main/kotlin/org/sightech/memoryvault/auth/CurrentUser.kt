package org.sightech.memoryvault.auth

import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.context.request.RequestContextHolder
import java.util.UUID

object CurrentUser {

    val SYSTEM_USER_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")

    /**
     * Returns the authenticated user's id. Inside a web request without an authenticated
     * UUID principal this fails closed. Outside any request (scheduler, MCP stdio, async
     * work) there is no caller to authenticate, so the system user is returned.
     */
    fun userId(): UUID {
        val principal = SecurityContextHolder.getContext().authentication?.principal
        if (principal is String) {
            try {
                return UUID.fromString(principal)
            } catch (e: IllegalArgumentException) {
                // fall through to the request check
            }
        }
        if (RequestContextHolder.getRequestAttributes() != null) {
            throw AccessDeniedException("No authenticated user")
        }
        return SYSTEM_USER_ID
    }
}
