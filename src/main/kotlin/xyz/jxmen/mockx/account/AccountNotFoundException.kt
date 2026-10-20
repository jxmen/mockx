package xyz.jxmen.mockx.account

import org.springframework.http.HttpStatus
import xyz.jxmen.mockx.common.web.ApiException

class AccountNotFoundException(accountId: Long): ApiException(
    status = HttpStatus.NOT_FOUND,
    code = "ACCOUNT_NOT_FOUND",
    message = "계좌 id '$accountId'를 찾을 수 없습니다."
)
