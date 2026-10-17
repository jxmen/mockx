import { describe, expect, it } from 'vitest'
import { ApiError, NetworkError } from '../../api/client'
import { toMyInfoErrorMessage } from './myInfoError'

describe('toMyInfoErrorMessage', () => {
  it('401 UNAUTHENTICATED는 헤더 형식 안내를 보여 준다', () => {
    const error = new ApiError(401, { status: 401, code: 'UNAUTHENTICATED', detail: 'UnAuthenticated' })
    expect(toMyInfoErrorMessage(error)).toBe('X-Member-Id는 1 이상의 숫자여야 합니다.')
  })

  it('404 MEMBER_NOT_FOUND는 회원이 없다고 알린다', () => {
    const error = new ApiError(404, { status: 404, code: 'MEMBER_NOT_FOUND', detail: '회원을 찾을 수 없습니다.' })
    expect(toMyInfoErrorMessage(error)).toBe('해당 번호의 회원이 없습니다.')
  })

  it('모르는 code면 서버의 detail을 보여 준다', () => {
    const error = new ApiError(500, { status: 500, code: 'SOMETHING', detail: '서버 오류' })
    expect(toMyInfoErrorMessage(error)).toBe('서버 오류')
  })

  it('본문이 Problem Details가 아니면 상태 코드를 보여 준다', () => {
    expect(toMyInfoErrorMessage(new ApiError(502, null))).toBe('요청에 실패했습니다. (HTTP 502)')
  })

  it('네트워크 오류면 백엔드 실행 여부를 안내한다', () => {
    expect(toMyInfoErrorMessage(new NetworkError())).toContain('서버에 연결할 수 없습니다.')
  })
})
