import type { SignupRequest } from '../../api/members'

export const SIGNUP_FIELDS = ['username', 'nickname', 'password', 'passwordConfirm'] as const

export type SignupField = (typeof SIGNUP_FIELDS)[number]
export type SignupForm = Record<SignupField, string>
export type SignupErrors = Partial<Record<SignupField, string>>

export const EMPTY_SIGNUP_FORM: SignupForm = {
  username: '',
  nickname: '',
  password: '',
  passwordConfirm: '',
}

// 서버 입력 규칙(docs/design/member.md 4장)과 같은 허용 목록
const USERNAME_PATTERN = /^[A-Za-z0-9_-]{2,10}$/
const NICKNAME_PATTERN = /^[가-힣A-Za-z0-9]{2,10}$/
const PASSWORD_PATTERN = /^[\x20-\x7E]{4,20}$/
const HANGUL_PATTERN = /[ㄱ-ㅎㅏ-ㅣ가-힣]/

export function isSignupField(value: unknown): value is SignupField {
  return typeof value === 'string' && (SIGNUP_FIELDS as readonly string[]).includes(value)
}

/**
 * 서버로 보낼 값을 만든다.
 * username, nickname은 앞뒤 공백을 지우고 NFC로 정규화한다(서버는 trim하지 않고, NFD 한글은 거절한다).
 * password는 공백도 유효한 문자이므로 그대로 보낸다.
 */
export function toSignupRequest(form: SignupForm): SignupRequest {
  return {
    username: form.username.trim().normalize('NFC'),
    nickname: form.nickname.trim().normalize('NFC'),
    password: form.password,
    passwordConfirm: form.passwordConfirm,
  }
}

/** 전송 전 안내용 검증. 최종 판정은 서버가 한다. 전송할 값(toSignupRequest 결과) 기준으로 검사한다. */
export function validateSignup(form: SignupForm): SignupErrors {
  const request = toSignupRequest(form)
  const errors: SignupErrors = {}

  if (!USERNAME_PATTERN.test(request.username)) {
    errors.username = '영문, 숫자, _, - 만 써서 2~10자로 입력해 주세요.'
  }
  if (!NICKNAME_PATTERN.test(request.nickname)) {
    errors.nickname = '한글, 영문, 숫자만 써서 2~10자로 입력해 주세요.'
  }
  if (!PASSWORD_PATTERN.test(request.password)) {
    errors.password = '4~20자로 입력해 주세요. 한글, 이모지, 탭은 쓸 수 없어요.'
  }
  if (request.passwordConfirm === '' || request.passwordConfirm !== request.password) {
    errors.passwordConfirm = '비밀번호가 일치하지 않습니다.'
  }
  return errors
}

/** 아이디 칸에 한글이 들어왔는지. 한/영 전환을 잊은 경우 안내하는 데 쓴다. */
export function containsHangul(value: string): boolean {
  return HANGUL_PATTERN.test(value)
}

/** 화면 순서상 첫 번째로 에러가 있는 필드 */
export function firstErrorField(errors: SignupErrors): SignupField | undefined {
  return SIGNUP_FIELDS.find((field) => errors[field] !== undefined)
}
