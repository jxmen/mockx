package xyz.jxmen.mockx.order

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import xyz.jxmen.mockx.common.money.Money
import xyz.jxmen.mockx.common.jpa.BaseTimeEntity
import java.time.Instant
import java.time.temporal.ChronoUnit

enum class OrderStatus {
    ACCEPTED, CANCELED
}

enum class OrderSide {
    BUY, SELL
}

/**
 * 주문
 */
@Entity
@Table(
    name = "orders",
    indexes = [
        Index(name = "idx_orders_account_id", columnList = "account_id"),
    ],
)
class Order(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0L,
    @Column(name = "account_id", nullable = false)
    var accountId: Long,
    @Column(name = "stock_id", nullable = false)
    var stockId: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "side", nullable = false, length = 10)
    var side: OrderSide,
    @Column(name = "price", nullable = false)
    var price: Long,
    @Column(name = "quantity", nullable = false)
    var quantity: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: OrderStatus,
    @Column(name = "canceled_at")
    var canceledAt: Instant? = null,
) : BaseTimeEntity() {
    companion object {
        /**
         * 매수 주문 접수.
         */
        fun createBuy(
            accountId: Long,
            stockId: Long,
            price: Long,
            quantity: Long,
        ): Order {
            require(price > 0 && quantity > 0) { "양수만 허용됩니다." }

            return Order(
                accountId = accountId,
                stockId = stockId,
                side = OrderSide.BUY,
                price = price,
                quantity = quantity,
                status = OrderStatus.ACCEPTED,
            )
        }
    }

    /**
     * 주문 취소.
     * DATETIME(6)은 마이크로초까지만 저장하므로 취소 시각도 마이크로초로 자른다(JpaConfig와 같은 이유).
     */
    fun cancel(accountId: Long): Money {
        if (this.accountId != accountId) throw OrderNotFoundException(this.id)
        if (status != OrderStatus.ACCEPTED) throw OrderNotCancelableException(this.id)

        this.status = OrderStatus.CANCELED
        this.canceledAt = Instant.now().truncatedTo(ChronoUnit.MICROS)

        return this.holdAmount()
    }

    /**
     * 이 주문이 묶은 금액. 지정가 × 수량
     */
    private fun holdAmount(): Money = Money(price * quantity)
}
