package xyz.jxmen.mockx.order

import java.time.Instant

data class OrderSummary(
    val id: Long,
    val symbol: String,
    val stockName: String,
    val side: OrderSide,
    val price: Long,
    val quantity: Long,
    val status: OrderStatus,
    val createdAt: Instant,
    val canceledAt: Instant?,
)
