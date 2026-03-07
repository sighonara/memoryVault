package org.sightech.memoryvault.auth.service

import org.junit.jupiter.api.Test
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import kotlin.test.assertTrue

class PasswordHashTest {
    private val passwordEncoder = BCryptPasswordEncoder()

    @Test
    fun `verify password hash for memoryvault`() {
        val hashInMigration = "$2b$12\$nDUurP9RwJl.cj36vBkDn.jRLEF/y/4V2aQ1VVJfQ05o/pcvjLXt6"
        val password = "memoryvault"

        // Let's see if the existing hash matches.
        // BCryptPasswordEncoder handles $2a$, $2b$, $2y$ by default.
        val matches = passwordEncoder.matches(password, hashInMigration)

        println("[DEBUG_LOG] Matches: $matches")
        assertTrue(matches, "Password hash should match 'memoryvault'")
    }
}
