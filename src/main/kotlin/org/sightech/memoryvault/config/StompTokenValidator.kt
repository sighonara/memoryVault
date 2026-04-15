package org.sightech.memoryvault.config

import java.security.Principal

interface StompTokenValidator {
    fun validate(token: String): Principal?
}
