package xyz.jxmen.mockx.order

import org.springframework.http.HttpStatus
import xyz.jxmen.mockx.common.web.ApiException

class OrderNotCancelableException(orderId: Long) : ApiException(
    status = HttpStatus.CONFLICT,
    code = "ORDER_NOT_CANCELABLE",
    message = "주문 id '$orderId'는 취소할 수 없는 상태입니다.",
)
