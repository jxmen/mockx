package xyz.jxmen.mockx.order

import java.time.Instant

/**
 * 내 주문 목록 응답. 금액(price)은 정수 센트다.
 * 배열을 객체로 감싸서 나중에 페이지네이션 정보를 계약을 깨지 않고 추가할 수 있게 한다(StocksResponse와 같다).
 */
data class GetOrdersResponse(
    val orders: List<OrderItem>,
) {
    data class OrderItem(
        val orderId: Long,
        val symbol: String,
        val stockName: String,
        val side: OrderSide,
        val price: Long,
        val quantity: Long,
        val status: OrderStatus,
        val createdAt: Instant,
        val canceledAt: Instant?, // 취소되지 않았으면 null. 필드를 빼지 않고 null로 응답한다
    )

    companion object {
        fun from(orderSummaries: List<OrderSummary>): GetOrdersResponse =
            GetOrdersResponse(
                orderSummaries.map {
                    OrderItem(
                        orderId = it.id,
                        symbol = it.symbol,
                        stockName = it.stockName,
                        side = it.side,
                        price = it.price,
                        quantity = it.quantity,
                        status = it.status,
                        createdAt = it.createdAt,
                        canceledAt = it.canceledAt,
                    )
                },
            )
    }
}
