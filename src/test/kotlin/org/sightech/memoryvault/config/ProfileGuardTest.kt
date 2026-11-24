package org.sightech.memoryvault.config

import org.junit.jupiter.api.Test
import org.springframework.mock.env.MockEnvironment
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ProfileGuardTest {

    private fun env(vararg profiles: String) = MockEnvironment().apply {
        setActiveProfiles(*profiles)
    }

    @Test
    fun `aws without prod fails`() {
        assertFailsWith<IllegalStateException> { ProfileGuard.check(env("aws")) }
    }

    @Test
    fun `prod without aws fails`() {
        assertFailsWith<IllegalStateException> { ProfileGuard.check(env("prod")) }
    }

    @Test
    fun `aws and prod ok`() {
        ProfileGuard.check(env("aws", "prod"))
    }

    @Test
    fun `local ok`() {
        ProfileGuard.check(env("local"))
    }

    @Test
    fun `test ok`() {
        ProfileGuard.check(env("test"))
    }

    @Test
    fun `aws prod local fails`() {
        assertFailsWith<IllegalStateException> { ProfileGuard.check(env("aws", "prod", "local")) }
    }

    @Test
    fun `aws prod test fails`() {
        assertFailsWith<IllegalStateException> { ProfileGuard.check(env("aws", "prod", "test")) }
    }

    @Test
    fun `no profiles fails and names SPRING_PROFILES_ACTIVE`() {
        val ex = assertFailsWith<IllegalStateException> { ProfileGuard.check(env()) }
        assertTrue(ex.message!!.contains("SPRING_PROFILES_ACTIVE"))
    }
}
