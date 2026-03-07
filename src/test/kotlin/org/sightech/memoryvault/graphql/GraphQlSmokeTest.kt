package org.sightech.memoryvault.graphql

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertNotNull

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class GraphQlSmokeTest {

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @Test
    fun `scalar config bean exists`() {
        // Verify ScalarConfig is loaded which provides UUID and Instant scalar support for GraphQL
        val scalarConfig = applicationContext.getBean(ScalarConfig::class.java)
        assertNotNull(scalarConfig, "ScalarConfig should be loaded")
    }
}
