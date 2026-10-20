package xyz.jxmen.mockx.account

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
