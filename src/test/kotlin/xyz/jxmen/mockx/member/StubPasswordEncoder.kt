package xyz.jxmen.mockx.member

class StubPasswordEncoder : IPasswordEncoder {
    override fun encode(raw: String) = "$raw!@"

    override fun matches(
        raw: String,
        hashed: String,
    ) = hashed == encode(raw)
}
