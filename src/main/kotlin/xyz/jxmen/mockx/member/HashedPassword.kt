package xyz.jxmen.mockx.member

import jakarta.persistence.Column
import jakarta.persistence.Embeddable

@ConsistentCopyVisibility
@Embeddable
data class HashedPassword private constructor(
    @Column(name = "password", nullable = false, length = 255)
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
