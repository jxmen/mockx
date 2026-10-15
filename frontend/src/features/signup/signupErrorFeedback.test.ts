import { describe, expect, it } from 'vitest'
import { ApiError, NetworkError, type ProblemDetail } from '../../api/client'
import { toSignupErrorFeedback } from './signupErrorFeedback'

function problem(status: number, extra: Partial<ProblemDetail>): ApiError {
  return new ApiError(status, { type: 'about:blank', status, instance: '/api/members', ...extra })
}

describe('toSignupErrorFeedback', () => {
  it('INVALID_INPUT의 errors는 필드별 메시지가 되고, 화면 순서상 첫 필드에 포커스한다', () => {
    const feedback = toSignupErrorFeedback(
      problem(400, {
        code: 'INVALID_INPUT',
        detail: '입력값이 올바르지 않습니다.',
        errors: [
          { field: 'nickname', detail: '2~10자여야 합니다.' },
          { field: 'username', detail: '2~10자여야 합니다.' },
        ],
      }),
    )

    expect(feedback).toEqual({
      fieldErrors: { username: '2~10자여야 합니다.', nickname: '2~10자여야 합니다.' },
      banner: null,
      focusField: 'username',
    })
  })

  it('errors가 없는 INVALID_INPUT(JSON 문법 오류)은 배너로 보여 준다', () => {
    const feedback = toSignupErrorFeedback(problem(400, { code: 'INVALID_INPUT', detail: '입력값이 올바르지 않습니다.' }))

    expect(feedback).toEqual({ fieldErrors: {}, banner: '입력값이 올바르지 않습니다.' })
  })

  it('USERNAME_DUPLICATED는 field가 가리키는 username에 붙인다', () => {
    const feedback = toSignupErrorFeedback(
      problem(409, { code: 'USERNAME_DUPLICATED', detail: '이미 사용 중인 username입니다.', field: 'username' }),
    )

    expect(feedback).toEqual({
      fieldErrors: { username: '이미 사용 중인 username입니다.' },
      banner: null,
      focusField: 'username',
    })
  })

  it('PASSWORD_NOT_MATCHED는 passwordConfirm에 붙인다', () => {
    const feedback = toSignupErrorFeedback(
      problem(400, { code: 'PASSWORD_NOT_MATCHED', detail: '비밀번호가 일치하지 않습니다.', field: 'passwordConfirm' }),
    )

    expect(feedback.fieldErrors).toEqual({ passwordConfirm: '비밀번호가 일치하지 않습니다.' })
    expect(feedback.focusField).toBe('passwordConfirm')
  })

  it('폼에 없는 필드 이름만 오면 배너로 보여 준다', () => {
    const feedback = toSignupErrorFeedback(
      problem(400, { code: 'INVALID_INPUT', detail: '입력값이 올바르지 않습니다.', errors: [{ field: 'role', detail: '?' }] }),
    )

    expect(feedback).toEqual({ fieldErrors: {}, banner: '입력값이 올바르지 않습니다.' })
  })

  it('5xx는 서버 detail 대신 일반 안내 문구를 보여 준다', () => {
    const feedback = toSignupErrorFeedback(problem(500, { detail: 'NullPointerException ...' }))

    expect(feedback.banner).toBe('일시적인 오류로 가입하지 못했어요. 잠시 후 다시 시도해 주세요.')
  })

  it('본문이 Problem Details가 아니면 일반 안내 문구를 보여 준다', () => {
    const feedback = toSignupErrorFeedback(new ApiError(404, null))

    expect(feedback.banner).toBe('일시적인 오류로 가입하지 못했어요. 잠시 후 다시 시도해 주세요.')
  })

  it('네트워크 오류는 연결 실패 문구를 보여 준다', () => {
    const feedback = toSignupErrorFeedback(new NetworkError())

    expect(feedback).toEqual({ fieldErrors: {}, banner: '서버에 연결할 수 없어요. 잠시 후 다시 시도해 주세요.' })
  })
})
