package org.sightech.memoryvault.auth

import org.springframework.security.core.context.SecurityContextHolder

object Roles {
    const val OWNER_ONLY = "hasRole('OWNER')"

    /**
     * True when the current caller holds ROLE_OWNER. With no authentication at all
     * (system/MCP context) the caller is the trusted owner channel, so this returns true.
     */
    fun isOwner(): Boolean {
        val auth = SecurityContextHolder.getContext().authentication ?: return true
        return auth.authorities.any { it.authority == "ROLE_OWNER" }
    }
}
