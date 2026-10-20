import { useEffect, useRef, useState, type FormEvent } from 'react'
import { deposit, type DepositResponse } from '../../api/accounts'
import { getMyInfo, type AccountInfo } from '../../api/members'
import { useMemberId } from '../../auth/memberIdContext'
import { formatAccountNumber } from '../../lib/accountNumber'
import { formatDollar } from '../../lib/money'
import { toMyInfoErrorMessage } from '../me/myInfoError'
import { ErrorIcon, FormField } from '../signup/FormField'
import { DEPOSIT_MAX, DEPOSIT_MIN, addToAmount, parseDepositAmount, toRawAmount } from './amount'
import { toDepositErrorFeedback } from './depositError'

type AccountState =
  | { status: 'loading' }
  | { status: 'error'; message: string }
  | { status: 'ready'; account: AccountInfo | null }

/** 이 화면에서 성공한 입금. 원장 조회 API가 생기기 전까지 화면 안에서만 보여 준다 */
type DepositRecord = DepositResponse & { seq: number; at: Date }

const QUICK_AMOUNTS = [100, 1_000, 5_000]

const timeFormatter = new Intl.DateTimeFormat('ko-KR', { hour: '2-digit', minute: '2-digit', second: '2-digit' })

export function DepositPage() {
  const { memberId } = useMemberId()

  return (
    <main className="page page--narrow">
      <h1 className="page__title">입금</h1>
      <p className="page__lead">
        위탁계좌에 예수금을 넣습니다. 1회 {formatDollar(DEPOSIT_MIN)} 이상 {formatDollar(DEPOSIT_MAX)} 이하, 달러
        단위 정수만 입금할 수 있어요.
      </p>

      {memberId === null ? (
        <section className="card">
          <p className="empty">상단 X-Member-Id에 회원번호를 입력하거나 먼저 회원가입을 해 주세요.</p>
        </section>
      ) : (
        // 회원을 바꾸면 입력값과 입금 내역을 모두 버리고 새로 시작한다
        <DepositPanel key={memberId} memberId={memberId} />
      )}
    </main>
  )
}

function DepositPanel({ memberId }: { memberId: string }) {
  const [accountState, setAccountState] = useState<AccountState>({ status: 'loading' })
  const [reloadToken, setReloadToken] = useState(0)
  const [history, setHistory] = useState<DepositRecord[]>([])

  useEffect(() => {
    const controller = new AbortController()
    getMyInfo(memberId, controller.signal)
      .then((data) => setAccountState({ status: 'ready', account: data.accounts[0] ?? null }))
      .catch((error: unknown) => {
        if (!controller.signal.aborted) {
          setAccountState({ status: 'error', message: toMyInfoErrorMessage(error) })
        }
      })
    return () => controller.abort()
  }, [memberId, reloadToken])

  function reload() {
    setAccountState({ status: 'loading' })
    setReloadToken((prev) => prev + 1)
  }

  function handleDeposited(response: DepositResponse) {
    setHistory((prev) => [{ ...response, seq: prev.length + 1, at: new Date() }, ...prev])
    setAccountState((prev) =>
      prev.status === 'ready' && prev.account !== null && prev.account.id === response.accountId
        ? { status: 'ready', account: { ...prev.account, balance: response.balance } }
        : prev,
    )
  }

  return (
    <>
      <section className="card" aria-live="polite" aria-busy={accountState.status === 'loading'}>
        {accountState.status === 'loading' ? (
          <p className="empty">불러오는 중…</p>
        ) : accountState.status === 'error' ? (
          <div className="stack">
            <div role="alert" className="banner">
              <ErrorIcon />
              <span>{accountState.message}</span>
            </div>
            <button type="button" className="button button--secondary button--block" onClick={reload}>
              다시 불러오기
            </button>
          </div>
        ) : accountState.account === null ? (
          <p className="empty">이 회원에게는 계좌가 없습니다.</p>
        ) : (
          <div className="stack">
            <AccountSummary account={accountState.account} onReload={reload} />
            <DepositForm memberId={memberId} account={accountState.account} onDeposited={handleDeposited} />
          </div>
        )}
      </section>

      {history.length > 0 && <DepositHistory history={history} />}
    </>
  )
}

function AccountSummary({ account, onReload }: { account: AccountInfo; onReload: () => void }) {
  return (
    <dl className="summary">
      <div className="summary__wide summary__head">
        <div>
          <dt>위탁계좌</dt>
          <dd className="mono summary__account">{formatAccountNumber(account.number)}</dd>
        </div>
        <button type="button" className="button button--secondary button--small" onClick={onReload}>
          새로고침
        </button>
      </div>
      <div>
        <dt>예수금</dt>
        <dd className="mono summary__amount">{formatDollar(account.balance)}</dd>
      </div>
      <div>
        <dt>주문 가능 금액</dt>
        <dd className="mono summary__amount">{formatDollar(account.balance - account.holdAmount)}</dd>
      </div>
    </dl>
  )
}

type DepositFormProps = {
  memberId: string
  account: AccountInfo
  onDeposited: (response: DepositResponse) => void
}

function DepositForm({ memberId, account, onDeposited }: DepositFormProps) {
  const [input, setInput] = useState('')
  const [touched, setTouched] = useState(false)
  const [serverError, setServerError] = useState<string | null>(null)
  const [banner, setBanner] = useState<string | null>(null)
  const [pending, setPending] = useState(false)
  const [lastDeposit, setLastDeposit] = useState<DepositResponse | null>(null)
  // 개발 모드에서 서버 검증(400 INVALID_INPUT)과 소유자 검증(404 ACCOUNT_NOT_FOUND)을 직접 확인하기 위한 값
  const [skipClientValidation, setSkipClientValidation] = useState(false)
  const [targetAccountId, setTargetAccountId] = useState('')

  const inputRef = useRef<HTMLInputElement>(null)
  const parsed = parseDepositAmount(input)
  const overridesAccount = targetAccountId.trim() !== ''

  const amountError =
    serverError ?? (skipClientValidation || !touched || parsed.ok ? undefined : parsed.error)

  const helper = parsed.ok
    ? overridesAccount
      ? `계좌 id ${targetAccountId.trim()}에 ${formatDollar(parsed.amount)} 입금을 요청합니다.`
      : `입금 후 예수금 ${formatDollar(account.balance + parsed.amount)} (예상)`
    : `${formatDollar(DEPOSIT_MIN)} ~ ${formatDollar(DEPOSIT_MAX)}, 정수로 입력해 주세요. 쉼표는 넣어도 됩니다.`

  function changeInput(value: string) {
    setInput(value)
    setServerError(null)
    setLastDeposit(null)
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    // 응답을 기다리는 동안 다시 누르면 같은 입금이 두 번 나간다. 서버에 Idempotency-Key가 없으므로 화면에서라도 막는다
    if (pending) {
      return
    }
    setTouched(true)
    setBanner(null)
    setLastDeposit(null)

    if (!skipClientValidation && !parsed.ok) {
      inputRef.current?.focus()
      return
    }
    // 개발용: 클라이언트 검증을 끄면 형식이 틀린 값(소수, 문자열, 빈 값)도 그대로 보낸다
    const amount = parsed.ok && !skipClientValidation ? parsed.amount : (toRawAmount(input) as number)
    const accountId = overridesAccount ? targetAccountId.trim() : account.id

    setPending(true)
    try {
      const response = await deposit(accountId, { amount }, memberId)
      onDeposited(response)
      setLastDeposit(response)
      setInput('')
      setTouched(false)
    } catch (error) {
      const feedback = toDepositErrorFeedback(error)
      setServerError(feedback.amountError)
      setBanner(feedback.banner)
      if (feedback.amountError !== null) {
        inputRef.current?.focus()
      }
    } finally {
      setPending(false)
    }
  }

  return (
    <form className="form" onSubmit={handleSubmit} noValidate>
      {banner !== null && (
        <div role="alert" className="banner">
          <ErrorIcon />
          <span>{banner}</span>
        </div>
      )}
      {lastDeposit !== null && (
        <div role="status" className="notice">
          <CheckIcon />
          <span>
            {formatDollar(lastDeposit.amount)} 입금했어요. 예수금은 {formatDollar(lastDeposit.balance)}입니다.
          </span>
        </div>
      )}

      <FormField
        id="deposit-amount"
        name="amount"
        label="입금액 (USD)"
        helper={helper}
        error={amountError}
        placeholder="예: 1,000"
        inputMode="numeric"
        autoComplete="off"
        value={input}
        inputRef={inputRef}
        onChange={(event) => changeInput(event.target.value)}
        onBlur={() => setTouched(true)}
        trailing={<span className="field__unit mono">$</span>}
      />

      <div className="chips" role="group" aria-label="금액 더하기">
        {QUICK_AMOUNTS.map((delta) => (
          <button
            key={delta}
            type="button"
            className="button button--secondary chip"
            onClick={() => changeInput(addToAmount(input, delta))}
          >
            +{formatDollar(delta)}
          </button>
        ))}
        <button
          type="button"
          className="button button--secondary chip"
          onClick={() => changeInput(String(DEPOSIT_MAX))}
        >
          최대
        </button>
      </div>

      <button type="submit" className="button button--primary button--block" disabled={pending} aria-busy={pending}>
        {pending ? '입금 중…' : '입금하기'}
      </button>

      {import.meta.env.DEV && (
        <fieldset className="dev-box">
          <legend>개발 모드에서만 보임</legend>
          <label className="dev-toggle">
            <input
              type="checkbox"
              checked={skipClientValidation}
              onChange={(event) => setSkipClientValidation(event.target.checked)}
            />
            <span>클라이언트 검증 끄기 (소수, 음수, 문자도 그대로 보내 서버 검증 확인)</span>
          </label>
          <label className="dev-field">
            <span>다른 계좌 id로 보내기 (소유자 검증 확인, 내 계좌 id: {account.id})</span>
            <input
              className="header__dev-input"
              inputMode="numeric"
              placeholder="내 계좌"
              value={targetAccountId}
              onChange={(event) => setTargetAccountId(event.target.value)}
            />
          </label>
        </fieldset>
      )}
    </form>
  )
}

function DepositHistory({ history }: { history: DepositRecord[] }) {
  return (
    <section className="card history" aria-labelledby="deposit-history-title">
      <h2 id="deposit-history-title" className="history__title">
        이 화면에서 한 입금
      </h2>
      <table className="history__table">
        <thead>
          <tr>
            <th scope="col">시각</th>
            <th scope="col" className="num">
              입금액
            </th>
            <th scope="col" className="num">
              입금 후 예수금
            </th>
          </tr>
        </thead>
        <tbody>
          {history.map((record) => (
            <tr key={record.seq}>
              <td className="mono">{timeFormatter.format(record.at)}</td>
              <td className="mono num">+{formatDollar(record.amount)}</td>
              <td className="mono num">{formatDollar(record.balance)}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <p className="history__note">새로고침하면 사라집니다. 전체 내역은 원장 조회 API가 생기면 보여 줄 수 있어요.</p>
    </section>
  )
}

function CheckIcon() {
  return (
    <svg className="icon" aria-hidden="true" width="16" height="16" viewBox="0 0 16 16">
      <circle cx="8" cy="8" r="6.5" />
      <path d="M5 8.2l2 2 4-4.4" />
    </svg>
  )
}
