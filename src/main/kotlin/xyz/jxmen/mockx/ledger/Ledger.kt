package xyz.jxmen.mockx.ledger

import jakarta.persistence.AttributeOverride
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import xyz.jxmen.mockx.account.Money
import xyz.jxmen.mockx.common.jpa.BaseTimeEntity

enum class LedgerType {
    DEPOSIT, // 입금

    // 매수, 매도 나중에 추가 예정
}

/**
 * 원장. 돈의 모든 입출금 내역
 *
 * 현재는 단식부기로 운영한다.
 */
@Entity
@Table(
    name = "ledger",
    indexes = [
        Index(name = "idx_ledger_account_id", columnList = "account_id"),
    ],
)
class Ledger private constructor(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,
    @Column(name = "account_id", nullable = false)
    val accountId: Long,
    @Embedded
    @AttributeOverride(name = "amount", column = Column(name = "amount"))
    val amount: Money,
    @Embedded
    @AttributeOverride(name = "amount", column = Column(name = "balance_after"))
    val balanceAfter: Money,
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    val type: LedgerType,
    // NOTE: 체결 ID 추후 추가 필요
) : BaseTimeEntity() {
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
