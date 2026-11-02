package xyz.jxmen.mockx.order

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import xyz.jxmen.mockx.common.web.InvalidInputException
import xyz.jxmen.mockx.common.web.CurrentMember

@RestController
@RequestMapping("/api/accounts/{accountId}/orders")
class OrderController(
    private val placeOrderService: PlaceOrderService,
    private val cancelOrderService: CancelOrderService,
    private val getOrdersService: GetOrdersService
) {

    @PostMapping
    fun placeOrder(
        @PathVariable accountId: Long,
        @CurrentMember memberId: Long,
        @RequestBody @Valid request: PlaceOrderRequest
    ): ResponseEntity<PlaceOrderResponse> {
        if (request.side == OrderSide.SELL) throw InvalidInputException("side", "매도 주문은 아직 지원하지 않습니다.")

        val placedOrder = placeOrderService.placeOrder(request.toCommand(accountId, memberId))

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(PlaceOrderResponse.from(placedOrder))
    }

    @PostMapping("/{orderId}/cancel")
    fun cancelOrder(
        @PathVariable accountId: Long,
        @PathVariable orderId: Long,
        @CurrentMember memberId: Long,
    ): ResponseEntity<CancelOrderResponse> {
        val canceledOrder = cancelOrderService.cancelOrder(
            CancelOrderCommand(accountId = accountId, memberId = memberId, orderId = orderId)
        )

        return ResponseEntity.status(HttpStatus.OK)
            .body(CancelOrderResponse.from(canceledOrder))
    }

    @GetMapping
    fun getOrders(
        @PathVariable accountId: Long,
        // 허용하지 않는 값은 Spring enum 변환에서 실패해 400이 된다. 빈 조각(?status=ACCEPTED,)은 null 원소로 들어온다
        @RequestParam(value = "side", required = false) sides: Set<OrderSide?>?,
        @RequestParam(value = "status", required = false) statuses: Set<OrderStatus?>?,
        @CurrentMember memberId: Long,
    ): ResponseEntity<GetOrdersResponse> {
        if (sides != null && (sides.isEmpty() || sides.contains(null))) {
            throw InvalidInputException("side", "빈 값은 허용되지 않습니다.")
        }
        if (statuses != null && (statuses.isEmpty() || statuses.contains(null))) {
            throw InvalidInputException("status", "빈 값은 허용되지 않습니다.")
        }

        val orderSummaries = getOrdersService.getOrders(
            GetOrdersCommand(
                accountId = accountId,
                memberId = memberId,
                sides = sides?.filterNotNull()?.toSet(),
                statuses = statuses?.filterNotNull()?.toSet(),
            )
        )

        return ResponseEntity.status(HttpStatus.OK)
            .body(GetOrdersResponse.from(orderSummaries))
    }
}
