package xyz.jxmen.mockx

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class HelloController {

    @GetMapping("/hello")
    fun hello(): Map<String, String> = mapOf("message" to "Hello, Mock Exchange!")
}
