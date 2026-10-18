package xyz.jxmen.mockx.ledger

import xyz.jxmen.mockx.account.Money

/**
 * 원장. 돈의 모든 입출금 내역
 *
 * 현재는 단식부기로 운영한다.
 */
class Ledger private constructor(
    val id: Long = 0L,
    val accountId: Long, // NOTE: 인덱스 필요
    val amount: Money,
    val balanceAfter: Money,
    val type: LedgerType,
    // NOTE: 체결 ID 추후 추가 필요
) {
    companion object {
        fun createDeposit(
            accountId: Long,
            amount: Money,
            balanceAfter: Money,
        ): Ledger {
            require(amount.isPositive()) { "입금액은 양수여야 합니다. (입금액: ${amount.amount})" }
            require(balanceAfter.isPositive()) { "입금 후 잔액은 양수여야 합니다. (입금 후 잔액: ${balanceAfter.amount})" }
            require(amount <= balanceAfter) {
                "입금 후 잔액은 입금액보다 작을 수 없습니다. (입금액: ${amount.amount}, 입금 후 잔액: ${balanceAfter.amount})"
            }

            return Ledger(
                accountId = accountId,
                amount = amount,
                balanceAfter = balanceAfter,
                type = LedgerType.DEPOSIT,
            )
        }
    }
}
