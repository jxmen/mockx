package xyz.jxmen.mockx.account

data class Money(
    val amount: Long = 0L,
) {
    operator fun plus(other: Money): Money = Money(amount + other.amount)

    operator fun minus(other: Money): Money = Money(amount - other.amount)

    operator fun compareTo(other: Money): Int = amount.compareTo(other.amount)

    fun isPositive(): Boolean = this.amount > 0L
}
