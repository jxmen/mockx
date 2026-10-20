import { ApiError, NetworkError } from '../../api/client'

export type DepositErrorFeedback = {
  /** 금액 칸 아래에 표시할 메시지 */
  amountError: string | null
  /** 폼 상단 배너. 금액 칸에 붙일 수 없는 에러일 때만 있다 */
  banner: string | null
  /** 입금이 반영됐는지 알 수 없다(응답을 못 받았거나 5xx). 예수금을 다시 확인하게 안내한다 */
  uncertain: boolean
}

const UNCERTAIN = '입금 결과를 확인하지 못했어요. 예수금을 새로고침해 반영됐는지 확인한 뒤 다시 시도해 주세요.'

/**
 * 입금 API 에러를 폼에 표시할 형태로 바꾼다. 분기는 code로 한다. docs/db-design-member.md 6장
 * - INVALID_INPUT → 금액 칸 메시지 (errors[]가 없는 형식 오류도 입력값은 amount 하나뿐이라 금액 칸에 붙인다)
 * - UNAUTHENTICATED, ACCOUNT_NOT_FOUND, 그 밖의 4xx → 배너
 * - 네트워크 오류, 5xx → 반영 여부를 알 수 없다는 배너
 */
export function toDepositErrorFeedback(error: unknown): DepositErrorFeedback {
  if (error instanceof NetworkError) {
    return { amountError: null, banner: UNCERTAIN, uncertain: true }
  }
  if (!(error instanceof ApiError)) {
    return { amountError: null, banner: '알 수 없는 오류가 발생했습니다.', uncertain: true }
  }
  if (error.status >= 500) {
    return { amountError: null, banner: UNCERTAIN, uncertain: true }
  }

  const problem = error.problem
  switch (problem?.code) {
    case 'INVALID_INPUT': {
      const detail = problem?.errors?.find((item) => item.field === 'amount')?.detail ?? problem?.detail
      return { amountError: detail ?? '입력값이 올바르지 않습니다.', banner: null, uncertain: false }
    }
    case 'UNAUTHENTICATED':
      return { amountError: null, banner: 'X-Member-Id는 1 이상의 숫자여야 합니다.', uncertain: false }
    case 'ACCOUNT_NOT_FOUND':
      // 서버는 남의 계좌도 없는 계좌와 똑같이 응답한다. 화면도 둘을 구분하지 않는다
      return { amountError: null, banner: '계좌를 찾을 수 없습니다. 내 계좌가 맞는지 확인해 주세요.', uncertain: false }
  }
  return {
    amountError: null,
    banner: problem?.detail ?? `요청에 실패했습니다. (HTTP ${error.status})`,
    uncertain: false,
  }
}
