package xyz.jxmen.mockx.member

interface IPasswordEncoder {
    fun encode(raw: String): String
    fun matches(raw: String, hashed: String): Boolean
}
