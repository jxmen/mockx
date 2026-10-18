import { useEffect, useState } from 'react'
import { getMyInfo, type AccountType, type MyInfoResponse } from '../../api/members'
import { useMemberId } from '../../auth/memberIdContext'
import { formatAccountNumber } from '../../lib/accountNumber'
import { ErrorIcon } from '../signup/FormField'
import { toMyInfoErrorMessage } from './myInfoError'

/** 어느 memberId로 받은 결과인지 함께 둔다. 지금 memberId와 다르면 아직 불러오는 중이다. */
type Result =
  | { memberId: string; status: 'success'; data: MyInfoResponse }
  | { memberId: string; status: 'error'; message: string }

const ACCOUNT_TYPE_LABEL: Record<AccountType, string> = {
  BROKERAGE: '위탁계좌',
}

export function MyInfoPage() {
  const { memberId } = useMemberId()
  const [result, setResult] = useState<Result | null>(null)

  // 상단 X-Member-Id를 바꿀 때마다 다시 조회한다. 입력 중 이전 요청은 취소해 늦게 온 응답이 덮어쓰지 않게 한다.
  useEffect(() => {
    if (memberId === null) {
      return
    }
    const controller = new AbortController()
    getMyInfo(memberId, controller.signal)
      .then((data) => setResult({ memberId, status: 'success', data }))
      .catch((error: unknown) => {
        if (!controller.signal.aborted) {
          setResult({ memberId, status: 'error', message: toMyInfoErrorMessage(error) })
        }
      })
    return () => controller.abort()
  }, [memberId])

  const current = result !== null && result.memberId === memberId ? result : null

  return (
    <main className="page page--narrow">
      <h1 className="page__title">내 정보</h1>
      <p className="page__lead">상단 X-Member-Id의 회원 정보와 계좌를 보여 줍니다.</p>

      <section className="card" aria-live="polite" aria-busy={memberId !== null && current === null}>
        {memberId === null ? (
          <p className="empty">상단 X-Member-Id에 회원번호를 입력하거나 먼저 회원가입을 해 주세요.</p>
        ) : current === null ? (
          <p className="empty">불러오는 중…</p>
        ) : current.status === 'error' ? (
          <div role="alert" className="banner">
            <ErrorIcon />
            <span>{current.message}</span>
          </div>
        ) : (
          <MyInfoSummary data={current.data} />
        )}
      </section>
    </main>
  )
}

function MyInfoSummary({ data }: { data: MyInfoResponse }) {
  return (
    <dl className="summary">
      <div>
        <dt>아이디</dt>
        <dd className="mono">{data.username}</dd>
      </div>
      <div>
        <dt>닉네임</dt>
        <dd>{data.nickname}</dd>
      </div>
      <div className="summary__wide">
        <dt>회원번호</dt>
        <dd className="mono">{data.memberId}</dd>
      </div>
      {data.accounts.map((account) => (
        <div key={account.id} className="summary__wide">
          <dt>{ACCOUNT_TYPE_LABEL[account.type] ?? account.type}</dt>
          <dd className="mono summary__account">{formatAccountNumber(account.number)}</dd>
        </div>
      ))}
    </dl>
  )
}
