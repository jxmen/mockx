package xyz.jxmen.mockx.account

import jakarta.persistence.AttributeOverride
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import xyz.jxmen.mockx.common.jpa.BaseTimeEntity

@Entity
@Table(
    name = "account",
    uniqueConstraints =
        [
            UniqueConstraint(name = "uk_account_member_id", columnNames = ["member_id"]),
            UniqueConstraint(name = "uk_account_number", columnNames = ["number"]),
        ],
)
class Account(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,
    @Column(name = "member_id", nullable = false)
    val memberId: Long,
    @Embedded
    val number: AccountNumber,
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    val type: AccountType,
): BaseTimeEntity() {
    companion object {
        fun create(
            memberId: Long,
            number: AccountNumber,
        ): Account =
            Account(
                memberId = memberId,
                number = number,
                type = AccountType.BROKERAGE,
            )
    }

    /**
     * 예수금.
     */
    @Embedded
    @AttributeOverride(name = "amount", column = Column(name = "balance"))
    var balance: Money = Money()
        protected set

    /**
     * 매수 주문을 넣어 묶인 돈. 예수금보다 클 수 없다.
     */
    @Embedded
    @AttributeOverride(name = "amount", column = Column(name = "hold_amount"))
    var holdAmount: Money = Money()
        protected set

    /**
     * 입금
     */
    fun deposit(money: Money): Money {
        require(money.isPositive()) { "입금할 금액은 양수여야만 합니다." }

        this.balance += money
        return this.balance
    }

    /**
     * money만큼의 돈 동결. 매수 주문 시 등에 사용
     */
    fun hold(money: Money) {
        require(money.isPositive()) { "주문할 금액은 양수여야만 합니다." }
        require(money <= availableAmount()) { "주문 가능 금액 초과" }

        this.holdAmount += money
    }

    /**
     * 취소, 차액 등
     */
    fun release(money: Money) {
        require(money.isPositive()) { "양수만 허용됩니다." }
        require(money <= holdAmount) { "hold된 금액만큼만 허용됩니다." }

        this.holdAmount -= money
    }

    fun availableAmount(): Money = (balance - holdAmount)
}
