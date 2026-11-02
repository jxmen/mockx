package xyz.jxmen.mockx.order

/**
 * 주문 접수 응답. 금액(price)은 정수 센트다.
 * enum은 이름 그대로 직렬화된다. (side: "BUY", status: "ACCEPTED")
 */
data class PlaceOrderResponse(
    val orderId: Long,
    val symbol: String,
    val side: OrderSide,
    val price: Long,
    val quantity: Long,
    val status: OrderStatus,
) {
    companion object {
        fun from(placedOrder: PlacedOrder): PlaceOrderResponse =
            PlaceOrderResponse(
                orderId = placedOrder.id,
                symbol = placedOrder.symbol,
                side = placedOrder.side,
                price = placedOrder.price,
                quantity = placedOrder.quantity,
                status = placedOrder.status,
            )
    }
}
