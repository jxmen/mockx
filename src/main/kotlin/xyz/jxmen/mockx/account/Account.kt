package xyz.jxmen.mockx.account

class Account(
    val id: Long = 0L,
    val memberId: Long,
) {
    /**
     * 예수금.
     */
    var balance: Money = Money()
        private set

    /**
     * 매수 주문을 넣어 묶인 돈. 예수금보다 클 수 없다.
     */
    var holdAmount: Money = Money()
        private set

    /**
     * 입금
     */
    fun deposit(money: Money) {
        require(money.isPositive()) { "입금할 금액은 양수여야만 합니다." }

        this.balance += money
    }

    /**
     * money만큼의 돈 동결. 매수 주문 시 등에 사용
     */
    fun hold(money: Money) {
        require(money.isPositive()) { "주문할 금액은 양수여야만 합니다." }
        require(money <= availableAmount()) { "주문 가능 금액 초과" }

        this.holdAmount += money
    }

    private fun availableAmount(): Money = (balance - holdAmount)
}
