package xyz.jxmen.mockx.order

/**
 * 필터가 null이면 그 조건으로 거르지 않는다.
 */
data class GetOrdersCommand(
    val accountId: Long,
    val memberId: Long,
    val sides: Set<OrderSide>? = null,
    val statuses: Set<OrderStatus>? = null,
)
