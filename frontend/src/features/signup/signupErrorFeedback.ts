import { ApiError, NetworkError } from '../../api/client'
import { firstErrorField, isSignupField, type SignupErrors, type SignupField } from './validation'

export type SignupErrorFeedback = {
  /** 필드 아래에 표시할 서버 메시지 */
  fieldErrors: SignupErrors
  /** 폼 상단 배너. 필드에 붙일 수 없는 에러일 때만 있다 */
  banner: string | null
  /** 포커스를 옮길 필드 */
  focusField?: SignupField
}

const UNKNOWN_ERROR = '일시적인 오류로 가입하지 못했어요. 잠시 후 다시 시도해 주세요.'
const NETWORK_ERROR = '서버에 연결할 수 없어요. 잠시 후 다시 시도해 주세요.'

/**
 * 회원가입 API 에러를 폼에 표시할 형태로 바꾼다.
 * - errors[] (INVALID_INPUT) → 필드별 메시지
 * - field (USERNAME_DUPLICATED, PASSWORD_NOT_MATCHED) → 해당 필드 메시지
 * - 그 외(JSON 문법 오류, 5xx, 네트워크 오류) → 배너
 * 메시지는 서버 detail을 그대로 쓴다.
 */
export function toSignupErrorFeedback(error: unknown): SignupErrorFeedback {
  if (error instanceof NetworkError) {
    return { fieldErrors: {}, banner: NETWORK_ERROR }
  }
  if (!(error instanceof ApiError) || error.problem === null || error.status >= 500) {
    return { fieldErrors: {}, banner: UNKNOWN_ERROR }
  }

  const problem = error.problem
  const fieldErrors: SignupErrors = {}
  for (const item of problem.errors ?? []) {
    if (isSignupField(item.field) && fieldErrors[item.field] === undefined) {
      fieldErrors[item.field] = item.detail
    }
  }
  if (isSignupField(problem.field) && problem.detail) {
    fieldErrors[problem.field] = problem.detail
  }

  const focusField = firstErrorField(fieldErrors)
  if (focusField === undefined) {
    return { fieldErrors: {}, banner: problem.detail ?? UNKNOWN_ERROR }
  }
  return { fieldErrors, banner: null, focusField }
}
