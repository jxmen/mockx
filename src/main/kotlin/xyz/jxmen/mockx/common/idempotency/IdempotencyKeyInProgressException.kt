package xyz.jxmen.mockx.common.idempotency

import org.springframework.http.HttpStatus
import xyz.jxmen.mockx.common.web.ApiException

class IdempotencyKeyInProgressException(key: IdempotencyKey) : ApiException(
    status = HttpStatus.CONFLICT,
    code = "IDEMPOTENCY_KEY_IN_PROGRESS",
    message = "Idempotency-Key '${key.key}'로 보낸 앞 요청이 아직 처리 중입니다. 잠시 뒤 같은 키로 다시 보내세요.",
)
