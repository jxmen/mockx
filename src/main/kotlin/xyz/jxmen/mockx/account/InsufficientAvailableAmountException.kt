package xyz.jxmen.mockx.account

import org.springframework.http.HttpStatus
import xyz.jxmen.mockx.common.web.ApiException

/**
 * hold하려는 금액이 주문가능금액(balance - holdAmount)보다 크다.
 * 요청 형식은 맞지만 지금 계좌 상태로는 처리할 수 없으므로 422다. (docs/db-design-order.md 12장 2번)
 */
class InsufficientAvailableAmountException(
    requested: Money,
    available: Money,
) : ApiException(
        status = HttpStatus.UNPROCESSABLE_CONTENT,
        code = "INSUFFICIENT_AVAILABLE_AMOUNT",
        message = "주문가능금액이 부족합니다. (요청: ${requested.value()}센트, 주문가능금액: ${available.value()}센트)",
    )
