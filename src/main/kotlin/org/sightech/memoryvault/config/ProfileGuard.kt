package org.sightech.memoryvault.config

import org.slf4j.LoggerFactory
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

/**
 * Fails startup when the active profiles are missing or inconsistent, so a misconfigured
 * deployment cannot silently fall back to local-development settings.
 */
@Component
class ProfileGuard(env: Environment) {

    private val log = LoggerFactory.getLogger(javaClass)

    init {
        val active = env.activeProfiles.toSet()
        check(active.isNotEmpty()) {
            "No Spring profile is active. Set SPRING_PROFILES_ACTIVE (e.g. 'local' for development or 'aws,prod' for production)."
        }
        if ("aws" in active || "prod" in active) {
            check("aws" in active && "prod" in active) {
                "Profiles 'aws' and 'prod' must be activated together; active profiles: $active"
            }
        }
        log.info("Active profiles validated: {}", active)
    }
}
