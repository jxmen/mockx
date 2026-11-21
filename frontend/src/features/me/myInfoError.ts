import { ApiError, NetworkError } from '../../api/client'

/** 내 정보 조회 실패를 화면에 보여 줄 문구로 바꾼다. 분기는 code로 한다. docs/design/member.md 6장 */
export function toMyInfoErrorMessage(error: unknown): string {
  if (error instanceof NetworkError) {
    return '서버에 연결할 수 없습니다. 백엔드(./gradlew bootRun)가 켜져 있는지 확인해 주세요.'
  }
  if (error instanceof ApiError) {
    switch (error.problem?.code) {
      case 'UNAUTHENTICATED':
        return 'X-Member-Id는 1 이상의 숫자여야 합니다.'
      case 'MEMBER_NOT_FOUND':
        return '해당 번호의 회원이 없습니다.'
    }
    return error.problem?.detail ?? `요청에 실패했습니다. (HTTP ${error.status})`
  }
  return '알 수 없는 오류가 발생했습니다.'
}
