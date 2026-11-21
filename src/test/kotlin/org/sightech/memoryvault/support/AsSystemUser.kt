package org.sightech.memoryvault.support

import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext
import org.sightech.memoryvault.auth.CurrentUser
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder

/**
 * For tests that call services directly. Spring's web test support binds a mock request to the
 * test thread, which makes CurrentUser require an authenticated principal; this supplies one.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@ExtendWith(AsSystemUserExtension::class)
annotation class AsSystemUser

class AsSystemUserExtension : BeforeEachCallback, AfterEachCallback {
    override fun beforeEach(context: ExtensionContext) {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(CurrentUser.SYSTEM_USER_ID.toString(), null, emptyList())
    }

    override fun afterEach(context: ExtensionContext) {
        SecurityContextHolder.clearContext()
    }
}
