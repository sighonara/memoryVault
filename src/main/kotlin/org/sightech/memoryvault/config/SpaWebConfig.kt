package org.sightech.memoryvault.config

import org.springframework.boot.web.error.ErrorPage
import org.springframework.boot.web.error.ErrorPageRegistrar
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus

@Configuration
class SpaWebConfig {

    @Bean
    fun errorPageRegistrar() = ErrorPageRegistrar { registry ->
        registry.addErrorPages(ErrorPage(HttpStatus.NOT_FOUND, "/index.html"))
    }
}
