package xyz.jxmen.mockx.common.idempotency

/**
 * 멱등성 캐시의 키. 키 범위가 회원별이라 다른 회원이 같은 Idempotency-Key를 보내도 섞이지 않는다.
 */
data class IdempotencyKey(
    val memberId: Long,
    val key: String,
)
