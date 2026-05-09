package org.sightech.memoryvault.billing

import org.junit.jupiter.api.Test
import org.sightech.memoryvault.billing.service.StripeService
import kotlin.test.assertNotNull

class StripeServiceTest {

    @Test
    fun `StripeService can be constructed without making real API calls`() {
        val service = StripeService(
            secretKey = "sk_test_disabled",
            webhookSecret = "whsec_disabled",
            premiumMonthlyPriceId = "price_pm",
            premiumAnnualPriceId = "price_pa",
            archiveMonthlyPriceId = "price_am",
            archiveAnnualPriceId = "price_aa"
        )
        assertNotNull(service)
    }
}
