package dev.moru3.pythonmapscreenapi.web

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod

@Controller
class WebController {
    @RequestMapping(path = ["/rts/screen"],method = [RequestMethod.GET])
    fun screen(): String { return "management" }
}