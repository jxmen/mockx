import { apiRequest } from './client'

export type SignupRequest = {
  username: string
  nickname: string
  password: string
  passwordConfirm: string
}

export type SignupResponse = {
  memberId: number
  username: string
  nickname: string
  /** 하이픈 없는 숫자 12자리 */
  accountNumber: string
}

export function signup(request: SignupRequest): Promise<SignupResponse> {
  return apiRequest<SignupResponse>('/api/members', { method: 'POST', body: request })
}
