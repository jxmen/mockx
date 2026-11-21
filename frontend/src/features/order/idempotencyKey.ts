import { ApiError, NetworkError } from '../../api/client'

// 주문 접수의 Idempotency-Key 생명주기. docs/design/order.md 15장 "클라이언트"
// 기준: 결과를 모르면 같은 키로 다시 보내고, 결과가 확정됐으면 키를 버린다.
// - 처음 주문 → 새 키
// - 네트워크 오류, 5xx, 409 IDEMPOTENCY_KEY_IN_PROGRESS → 같은 키로 재시도(서버가 한 번만 접수한다)
// - 201, 그 밖의 4xx → 키 버림. 다음 주문은 새 키
// - 종목·가격·수량 변경 → 키 버림(다른 주문 의도). 이 판단은 화면(OrderPage)이 한다
// 키는 화면 메모리에만 둔다. 새로고침하면 사라진다

/**
 * 주문 접수가 실패했을 때 다음 시도에 같은 Idempotency-Key를 다시 써야 하는지.
 * true면 결과를 모르는 실패라 같은 키로 보내야 두 번 접수되지 않는다. false면 확정된 실패라 키를 버린다.
 * toPlaceOrderErrorFeedback의 uncertain과 같은 기준이다.
 */
export function shouldReuseIdempotencyKey(error: unknown): boolean {
  if (error instanceof NetworkError) {
    return true
  }
  if (!(error instanceof ApiError)) {
    // 응답을 해석하지 못했다(예: 201 본문 파싱 실패). 접수됐을 수 있다
    return true
  }
  if (error.status >= 500) {
    return true
  }
  return error.status === 409 && error.problem?.code === 'IDEMPOTENCY_KEY_IN_PROGRESS'
}
