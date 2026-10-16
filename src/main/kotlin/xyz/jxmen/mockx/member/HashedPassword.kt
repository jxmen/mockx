package xyz.jxmen.mockx.member

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import org.springframework.security.crypto.password.PasswordEncoder

@ConsistentCopyVisibility
@Embeddable
data class HashedPassword private constructor(
    @Column(name = "password", nullable = false, length = 255)
    private val value: String,
) {
    companion object {
        fun withEncoder(
            raw: String,
            encoder: PasswordEncoder,
        ): HashedPassword {
            val encoded = encoder.encode(raw)
            checkNotNull(encoded)

            return HashedPassword(encoded)
        }
    }

    fun value() = value

    override fun toString(): String = "****"
}
