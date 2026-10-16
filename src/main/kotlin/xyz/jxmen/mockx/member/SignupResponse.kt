package xyz.jxmen.mockx.member

import xyz.jxmen.mockx.account.Account

data class SignupResponse(
    val memberId: Long,
    val username: String,
    val nickname: String,
    val accountNumber: String, // 하이픈 없는 12자리
) {
    companion object {
        fun of(
            member: Member,
            account: Account,
        ): SignupResponse =
            SignupResponse(
                memberId = member.id,
                username = member.username,
                nickname = member.nickname,
                accountNumber = account.number.number,
            )
    }
}
