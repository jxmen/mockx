package xyz.jxmen.mockx.order

import org.springframework.http.HttpStatus
import xyz.jxmen.mockx.common.web.ApiException

class OrderNotFoundException(orderId: Long) : ApiException(
    status = HttpStatus.NOT_FOUND,
    code = "ORDER_NOT_FOUND",
    message = "주문 id '$orderId'를 찾을 수 없습니다.",
)
