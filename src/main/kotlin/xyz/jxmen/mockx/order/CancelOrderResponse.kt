package xyz.jxmen.mockx.order

import java.time.Instant

/**
 * 주문 취소 응답. 금액(price)은 정수 센트다.
 */
data class CancelOrderResponse(
    val orderId: Long,
    val symbol: String,
    val side: OrderSide,
    val price: Long,
    val quantity: Long,
    val status: OrderStatus,
    val canceledAt: Instant,
) {
    companion object {
        fun from(canceledOrder: CanceledOrder): CancelOrderResponse {
            return CancelOrderResponse(
                orderId = canceledOrder.id,
                symbol = canceledOrder.symbol,
                side = canceledOrder.side,
                price = canceledOrder.price,
                quantity = canceledOrder.quantity,
                status = canceledOrder.status,
                canceledAt = canceledOrder.canceledAt,
            )
        }
    }
}
