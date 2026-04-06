package org.sightech.memoryvault.config

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.RequestMapping

@Controller
class SpaForwardingController {

    @RequestMapping(
        value = [
            "/{path:^(?!api|actuator|graphql|graphiql|ws|stomp).*$}",
            "/{path:^(?!api|actuator|graphql|graphiql|ws|stomp).*$}/**"
        ]
    )
    fun forward(): String = "forward:/index.html"
}
