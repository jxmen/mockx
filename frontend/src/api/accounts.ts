import { apiRequest } from './client'

export type DepositRequest = {
  /** 달러 단위 정수. 1회 1 이상 10,000 이하 */
  amount: number
}

export type DepositResponse = {
  accountId: number
  /** 입금액(달러) */
  amount: number
  /** 입금 후 예수금(달러) */
  balance: number
}

/** 내 계좌에 입금한다. 남의 계좌나 없는 계좌면 404 ACCOUNT_NOT_FOUND */
export function deposit(
  accountId: number | string,
  request: DepositRequest,
  memberId: string | null,
): Promise<DepositResponse> {
  return apiRequest<DepositResponse>(`/api/accounts/${encodeURIComponent(accountId)}/deposit`, {
    method: 'POST',
    body: request,
    memberId,
  })
}
