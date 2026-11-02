package xyz.jxmen.mockx.account

import xyz.jxmen.mockx.common.money.Money

data class DepositResponse(
    val accountId: Long,
    val amount: Long,
    val balance: Long,
) {
    companion object {
        fun of(
            accountId: Long,
            money: Money,
            balance: Money,
        ): DepositResponse = DepositResponse(accountId, money.value(), balance.value())
    }
}
