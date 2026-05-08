package org.sightech.memoryvault.billing.tier

import org.sightech.memoryvault.auth.entity.Tier
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

@ResponseStatus(HttpStatus.FORBIDDEN)
class TierRequiredException(required: Tier) :
    RuntimeException("This feature requires ${required.name} tier or higher")
