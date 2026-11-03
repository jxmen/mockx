package xyz.jxmen.mockx.common.idempotency

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.stereotype.Component
import java.time.Duration

sealed interface IdempotencyStatus {
    val fp: Any

    data class Pending(override val fp: Any) : IdempotencyStatus
    data class Completed(override val fp: Any, val data: Any) : IdempotencyStatus
}


@Component
class IdempotentExecutor {

    companion object {
        private val TTL = Duration.ofHours(24)
    }

    private val cache = Caffeine.newBuilder()
        .expireAfterWrite(TTL)
        .build<IdempotencyKey, IdempotencyStatus>()

    fun <R : Any> execute(key: IdempotencyKey, fp: Any, action: () -> R): IdempotencyResult<R> {
        // putIfAbsent: 있으면 조회하고, 없으면 key의 value에 있는 값으로 넣는다
        return when (val existing = cache.asMap().putIfAbsent(key, IdempotencyStatus.Pending(fp))) {
            null -> {
                runCatching { action() }
                    .onSuccess { data -> cache.asMap()[key] = IdempotencyStatus.Completed(fp = fp, data = data) }
                    .onFailure { cache.asMap().remove(key) }
                    .map { data -> IdempotencyResult(replayed = false, data = data) }
                    .getOrThrow()
            }

            is IdempotencyStatus.Pending -> {
                throw IdempotencyKeyInProgressException(key)
            }

            is IdempotencyStatus.Completed -> {
                if (existing.fp != fp) throw IdempotencyKeyReusedException(key)

                @Suppress("UNCHECKED_CAST") // 같은 키는 같은 action 결과만 들어간다.
                IdempotencyResult(replayed = true, existing.data as R)
            }
        }
    }
}
