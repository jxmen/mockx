package xyz.jxmen.mockx.member

class Member(
    val username: String,
    val nickname: String,
    val password: HashedPassword,
) {
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
