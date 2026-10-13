package xyz.jxmen.mockx.member

import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import xyz.jxmen.mockx.common.jpa.BaseTimeEntity

@Entity
class Member(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,
    @Column(name = "username", nullable = false, length = 10)
    val username: String,
    @Column(name = "nickname", nullable = false, length = 10)
    val nickname: String,
    @Embedded
    val password: HashedPassword,
): BaseTimeEntity() {
    companion object {
        fun create(
            username: String,
            nickname: String,
            hashedPassword: HashedPassword,
        ): Member =
            Member(
                username = username,
                nickname = nickname,
                password = hashedPassword,
            )
    }
}
