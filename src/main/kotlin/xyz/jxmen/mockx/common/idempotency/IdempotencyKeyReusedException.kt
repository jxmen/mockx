package xyz.jxmen.mockx.common.idempotency

import org.springframework.http.HttpStatus
import xyz.jxmen.mockx.common.web.ApiException

class IdempotencyKeyReusedException(key: IdempotencyKey) : ApiException(
    status = HttpStatus.UNPROCESSABLE_CONTENT,
    code = "IDEMPOTENCY_KEY_REUSED",
    message = "Idempotency-Key '${key.key}'는 내용이 다른 요청에 이미 사용됐습니다. 새 요청이면 새 키를 쓰세요.",
)
