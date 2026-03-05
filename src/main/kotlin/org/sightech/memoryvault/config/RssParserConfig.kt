package org.sightech.memoryvault.config

import com.prof18.rssparser.RssParser
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RssParserConfig {

    @Bean
    fun rssParser(): RssParser = RssParser()
}
