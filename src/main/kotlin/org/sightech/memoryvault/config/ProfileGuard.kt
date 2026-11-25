package org.sightech.memoryvault.config

import org.springframework.boot.EnvironmentPostProcessor
import org.springframework.boot.SpringApplication
import org.springframework.core.Ordered
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.Environment

/**
 * Validates the active profiles. Runs as an EnvironmentPostProcessor so a bad configuration
 * fails before the web server or any bean is created, instead of surfacing as an unrelated
 * missing-bean error (or silently starting in the wrong mode).
 */
object ProfileGuard {

    private val deployedProfiles = setOf("aws", "prod")
    private val devProfiles = setOf("local", "test")

    fun check(env: Environment) {
        val active = env.activeProfiles.toSet()
        check(active.isNotEmpty()) {
            "No Spring profile is active. Set SPRING_PROFILES_ACTIVE (e.g. 'local' for development or 'aws,prod' for production)."
        }
        val deployed = active.intersect(deployedProfiles)
        if (deployed.isNotEmpty()) {
            check(deployed == deployedProfiles) {
                "Profiles 'aws' and 'prod' must be activated together; active profiles: $active"
            }
            check(active.intersect(devProfiles).isEmpty()) {
                "Profiles 'local' and 'test' must not be combined with 'aws' or 'prod'; active profiles: $active"
            }
        }
    }
}

class ProfileGuardEnvironmentPostProcessor : EnvironmentPostProcessor, Ordered {

    override fun postProcessEnvironment(environment: ConfigurableEnvironment, application: SpringApplication) {
        ProfileGuard.check(environment)
    }

    // Run after config data (application*.properties) has been loaded so that profiles
    // declared there are included in the check.
    override fun getOrder(): Int = Ordered.LOWEST_PRECEDENCE
}
