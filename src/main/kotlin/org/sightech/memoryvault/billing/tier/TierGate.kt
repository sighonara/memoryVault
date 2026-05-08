package org.sightech.memoryvault.billing.tier

import org.sightech.memoryvault.auth.entity.Tier

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class TierGate(val value: Tier)
