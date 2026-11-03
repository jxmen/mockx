package xyz.jxmen.mockx

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * 주문 접수 요청에 붙일 새 Idempotency-Key. UUIDv7이다.
 * - 멱등성 캐시는 테이블을 비워도 남고, member를 TRUNCATE하면 memberId가 1부터 다시 시작한다.
 *   고정 키를 쓰면 앞 테스트에서 저장한 응답이 재응답으로 돌아오므로 의도 하나마다 새로 만든다.
 */
@OptIn(ExperimentalUuidApi::class)
fun newIdempotencyKey(): String = Uuid.generateV7().toString()
