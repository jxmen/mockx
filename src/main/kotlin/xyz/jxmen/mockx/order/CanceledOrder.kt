package xyz.jxmen.mockx.order

import java.time.Instant

data class CanceledOrder(
    val id: Long,
    val symbol: String,
    val side: OrderSide,
    val price: Long,
    val quantity: Long,
    val status: OrderStatus,
    val canceledAt: Instant,
) {
    companion object {
        fun of(order: Order, symbol: String): CanceledOrder {
            return CanceledOrder(
                id = order.id,
                symbol = symbol,
                side = order.side,
                price = order.price,
                quantity = order.quantity,
                status = order.status,
                canceledAt = checkNotNull(order.canceledAt)
            )
        }
    }
}
