package xyz.jxmen.mockx.member

import xyz.jxmen.mockx.account.Account
import xyz.jxmen.mockx.account.AccountType

data class AccountInfo(
    val id: Long,
    val type: AccountType,
    val number: String,
    val balance: Long,
    val holdAmount: Long,
)

data class MyInfoResponse(
    val memberId: Long,
    val username: String,
    val nickname: String,
    val accounts: List<AccountInfo>
) {
    companion object {
        fun of(member: Member, account: Account): MyInfoResponse {
            return MyInfoResponse(
                memberId = member.id,
                username = member.username,
                nickname = member.nickname,
                accounts = listOf(AccountInfo(
                    id = account.id,
                    type = account.type,
                    number = account.number.value(),
                    balance = account.balance.value(),
                    holdAmount = account.holdAmount.value()
                ))
            )
        }
    }

}
