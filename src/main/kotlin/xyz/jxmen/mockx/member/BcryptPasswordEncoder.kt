package xyz.jxmen.mockx.member

import org.springframework.stereotype.Component

@Component
class BcryptPasswordEncoder: IPasswordEncoder {
    override fun encode(raw: String): String {
        TODO("Not yet implemented")
    }

    override fun matches(raw: String, hashed: String): Boolean {
        TODO("Not yet implemented")
    }
}
