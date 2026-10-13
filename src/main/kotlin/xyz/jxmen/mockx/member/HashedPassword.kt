package xyz.jxmen.mockx.member

@ConsistentCopyVisibility
data class HashedPassword private constructor(
    private val value: String,
) {
    companion object {
        fun withEncoder(
            raw: String,
            encoder: IPasswordEncoder,
        ): HashedPassword = HashedPassword(encoder.encode(raw))
    }

    fun value() = value

    override fun toString(): String = "****"
}
