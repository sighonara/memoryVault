package org.sightech.memoryvault.config.controller

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class PublicConfig(
    val cognito: CognitoConfig
)

data class CognitoConfig(
    val userPoolId: String,
    val clientId: String,
    val region: String
)

@RestController
@RequestMapping("/api/config")
class ConfigController(
    @Value("\${memoryvault.cognito.user-pool-id:}") private val cognitoUserPoolId: String,
    @Value("\${memoryvault.cognito.client-id:}") private val cognitoClientId: String,
    @Value("\${memoryvault.cognito.region:us-east-1}") private val cognitoRegion: String
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping
    fun getConfig(): PublicConfig {
        log.debug("Returning public config (cognitoConfigured={})", cognitoUserPoolId.isNotBlank())
        return PublicConfig(
            cognito = CognitoConfig(
                userPoolId = cognitoUserPoolId,
                clientId = cognitoClientId,
                region = cognitoRegion
            )
        )
    }
}
