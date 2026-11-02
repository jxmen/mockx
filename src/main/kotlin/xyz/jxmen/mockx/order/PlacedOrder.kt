package xyz.jxmen.mockx.order

data class PlacedOrder(
    val id: Long,
    val symbol: String,
    val side: OrderSide,
    val price: Long,
    val quantity: Long,
    val status: OrderStatus
) {
    companion object {
        /**
         * Order는 stock_id만 가지므로 symbol은 따로 받는다. 정규화된 값(stock.symbol)을 넘겨야 한다.
         */
        fun of(order: Order, symbol: String): PlacedOrder =
            PlacedOrder(
                id = order.id,
                symbol = symbol,
                side = order.side,
                price = order.price,
                quantity = order.quantity,
                status = order.status,
            )
    }
}
