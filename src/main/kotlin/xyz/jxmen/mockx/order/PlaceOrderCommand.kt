package xyz.jxmen.mockx.order

import xyz.jxmen.mockx.account.Money

data class PlaceOrderCommand(
    val accountId: Long,
    val memberId: Long,
    val symbol: String,
    val side: OrderSide,
    val price: Long,
    val quantity: Long,
) {
    fun holdAmount(): Money {
        return Money(price * quantity)
    }

    fun toBuyOrder(stockId: Long): Order {
        return Order.createBuy(
            accountId = accountId,
            stockId = stockId,
            price = price,
            quantity = quantity,
        )
    }
}
