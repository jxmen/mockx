package xyz.jxmen.mockx.common.idempotency

/**
 * 멱등성 키로 감싼 실행의 결과.
 */
data class IdempotencyResult<T>(
    val replayed: Boolean,
    val data: T,
)
