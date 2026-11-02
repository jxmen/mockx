package xyz.jxmen.mockx.order

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface OrderRepository: JpaRepository<Order, Long> {

    /**
     * 계좌의 주문을 종목과 조인해 최신순으로 읽는다. 연관관계 매핑이 없으므로 ON으로 조인한다.
     * sides, statuses는 비어 있으면 안 된다. 필터가 없으면 enum 전체를 넘긴다.
     */
    @Query("""
            SELECT NEW xyz.jxmen.mockx.order.OrderSummary(
                o.id, s.symbol, s.name, o.side, o.price, o.quantity, o.status, o.createdAt, o.canceledAt
            )
            FROM Order o JOIN Stock s ON o.stockId = s.id
            WHERE o.accountId = :accountId
            AND o.side IN :sides
            AND o.status IN :statuses
            ORDER BY o.id DESC
        """)
    fun findSummaries(
        @Param("accountId") accountId: Long,
        @Param("sides") sides: Collection<OrderSide>,
        @Param("statuses") statuses: Collection<OrderStatus>,
    ): List<OrderSummary>
}
