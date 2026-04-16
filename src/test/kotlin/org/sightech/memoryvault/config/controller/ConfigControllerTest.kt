package org.sightech.memoryvault.config.controller

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ConfigControllerTest {

    @Test
    fun `returns configured cognito values`() {
        val controller = ConfigController(
            cognitoUserPoolId = "us-east-1_abc123",
            cognitoClientId = "client123",
            cognitoRegion = "us-east-1"
        )

        val config = controller.getConfig()

        assertEquals("us-east-1_abc123", config.cognito.userPoolId)
        assertEquals("client123", config.cognito.clientId)
        assertEquals("us-east-1", config.cognito.region)
    }

    @Test
    fun `returns empty cognito values when unconfigured`() {
        val controller = ConfigController(
            cognitoUserPoolId = "",
            cognitoClientId = "",
            cognitoRegion = "us-east-1"
        )

        val config = controller.getConfig()

        assertEquals("", config.cognito.userPoolId)
        assertEquals("", config.cognito.clientId)
        assertEquals("us-east-1", config.cognito.region)
    }
}
