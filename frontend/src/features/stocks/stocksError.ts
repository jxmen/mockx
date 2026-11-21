import { ApiError, NetworkError } from '../../api/client'

/**
 * 종목 목록 조회 실패를 화면에 보여 줄 문구로 바꾼다. 분기는 code로 한다. docs/design/stock.md 4.5
 * - INVALID_INPUT → errors[]의 sort 메시지 (허용하지 않은 필드, 잘못된 방향, 정렬 기준 여러 개)
 * - 조회라서 돈이 움직이지 않는다. 네트워크 오류, 5xx도 다시 불러오면 된다
 */
export function toStocksErrorMessage(error: unknown): string {
  if (error instanceof NetworkError) {
    return '서버에 연결할 수 없습니다. 백엔드(./gradlew bootRun)가 켜져 있는지 확인해 주세요.'
  }
  if (error instanceof ApiError) {
    const problem = error.problem
    if (problem?.code === 'INVALID_INPUT') {
      const detail = problem.errors?.find((item) => item.field === 'sort')?.detail ?? problem.detail
      return `정렬 조건이 올바르지 않습니다. ${detail ?? ''}`.trim()
    }
    return problem?.detail ?? `요청에 실패했습니다. (HTTP ${error.status})`
  }
  return '알 수 없는 오류가 발생했습니다.'
}
