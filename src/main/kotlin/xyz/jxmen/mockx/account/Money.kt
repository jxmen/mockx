package xyz.jxmen.mockx.account

import jakarta.persistence.Embeddable

/**
 * USD 금액. 단위는 센트 정수다($1 = 100, $187.43 = 18743). DB 컬럼과 API 요청·응답도 같은 단위를 쓴다.
 */
@Embeddable
data class Money(
    val amount: Long = 0L,
) {
    operator fun plus(other: Money): Money = Money(amount + other.amount)

    operator fun minus(other: Money): Money = Money(amount - other.amount)

    operator fun compareTo(other: Money): Int = amount.compareTo(other.amount)

    fun isPositive(): Boolean = this.amount > 0L
    fun value(): Long = amount
}
