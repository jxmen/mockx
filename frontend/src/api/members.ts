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

export type AccountType = 'BROKERAGE'

export type AccountInfo = {
  id: number
  type: AccountType
  /** 하이픈 없는 숫자 12자리 */
  number: string
  /** 예수금(달러) */
  balance: number
  /** 매수 주문으로 묶인 금액(달러) */
  holdAmount: number
}

export type MyInfoResponse = {
  memberId: number
  username: string
  nickname: string
  accounts: AccountInfo[]
}

export function getMyInfo(memberId: string | null, signal?: AbortSignal): Promise<MyInfoResponse> {
  return apiRequest<MyInfoResponse>('/api/members/me', { memberId, signal })
}
