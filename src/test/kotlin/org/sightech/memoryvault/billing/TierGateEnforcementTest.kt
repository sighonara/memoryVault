package org.sightech.memoryvault.billing

import org.junit.jupiter.api.Test
import org.sightech.memoryvault.auth.entity.Tier
import org.sightech.memoryvault.billing.tier.TierGate
import org.sightech.memoryvault.youtube.service.YoutubeListService
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TierGateEnforcementTest {

    @Test
    fun `YoutubeListService addList is annotated with TierGate PREMIUM`() {
        val method = YoutubeListService::class.java.getDeclaredMethod("addList", String::class.java)
        val annotation = method.getAnnotation(TierGate::class.java)
        assertNotNull(annotation) { "addList must be annotated with @TierGate" }
        assertEquals(Tier.PREMIUM, annotation.value)
    }

    @Test
    fun `YoutubeListService refreshList is annotated with TierGate PREMIUM`() {
        val method = YoutubeListService::class.java.getDeclaredMethod("refreshList", java.util.UUID::class.java)
        val annotation = method.getAnnotation(TierGate::class.java)
        assertNotNull(annotation) { "refreshList must be annotated with @TierGate" }
        assertEquals(Tier.PREMIUM, annotation.value)
    }
}
