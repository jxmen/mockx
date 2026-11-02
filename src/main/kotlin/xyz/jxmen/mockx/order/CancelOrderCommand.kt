package xyz.jxmen.mockx.order

data class CancelOrderCommand(
    val accountId: Long,
    val memberId: Long,
    val orderId: Long,
)
