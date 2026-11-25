package org.sightech.memoryvault.auth

import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.context.request.RequestContextHolder

object Roles {
    const val OWNER_ONLY = "hasRole('OWNER')"

    /**
     * True when the current caller holds ROLE_OWNER. With no authentication outside a web
     * request (system/MCP context) the caller is the trusted owner channel, so this returns
     * true; with no authentication inside a web request it returns false.
     */
    fun isOwner(): Boolean {
        val auth = SecurityContextHolder.getContext().authentication
            ?: return RequestContextHolder.getRequestAttributes() == null
        return auth.authorities.any { it.authority == "ROLE_OWNER" }
    }
}
