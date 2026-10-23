package xyz.jxmen.mockx.stock

import org.springframework.http.HttpStatus
import xyz.jxmen.mockx.common.web.ApiException

class StockNotFoundException(
    symbol: String,
) : ApiException(
        status = HttpStatus.NOT_FOUND,
        code = "STOCK_NOT_FOUND",
        message = "symbol '$symbol'에 대한 주식을 찾을 수 없습니다.",
    )
