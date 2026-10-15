import { useEffect, useRef, useState, type FormEvent } from 'react'
import { signup, type SignupResponse } from '../../api/members'
import { useMemberId } from '../../auth/memberIdContext'
import { formatAccountNumber } from '../../lib/accountNumber'
import { ErrorIcon, FormField } from './FormField'
import { toSignupErrorFeedback } from './signupErrorFeedback'
import {
  EMPTY_SIGNUP_FORM,
  containsHangul,
  firstErrorField,
  toSignupRequest,
  validateSignup,
  type SignupErrors,
  type SignupField,
  type SignupForm,
} from './validation'

type FieldSpec = {
  field: SignupField
  label: string
  helper: string
  placeholder: string
  secret: boolean
}

const FIELD_SPECS: FieldSpec[] = [
  {
    field: 'username',
    label: '아이디',
    helper: '영문, 숫자, _, - 2~10자. 대소문자만 다른 아이디는 같은 아이디로 봅니다.',
    placeholder: '예: Alice',
    secret: false,
  },
  {
    field: 'nickname',
    label: '닉네임',
    helper: '한글, 영문, 숫자 2~10자. 다른 회원과 겹쳐도 됩니다.',
    placeholder: '예: 앨리스',
    secret: false,
  },
  {
    field: 'password',
    label: '비밀번호',
    helper: '4~20자. 영문, 숫자, 특수문자, 공백을 쓸 수 있어요.',
    placeholder: '4~20자',
    secret: true,
  },
  {
    field: 'passwordConfirm',
    label: '비밀번호 확인',
    helper: '비밀번호를 한 번 더 입력해 주세요.',
    placeholder: '한 번 더 입력',
    secret: true,
  },
]

export function SignupPage() {
  const { setMemberId } = useMemberId()

  const [form, setForm] = useState<SignupForm>(EMPTY_SIGNUP_FORM)
  const [touched, setTouched] = useState<Partial<Record<SignupField, boolean>>>({})
  const [submitted, setSubmitted] = useState(false)
  const [serverErrors, setServerErrors] = useState<SignupErrors>({})
  const [banner, setBanner] = useState<string | null>(null)
  const [pending, setPending] = useState(false)
  const [showPassword, setShowPassword] = useState(false)
  // 개발 모드에서 서버 검증(400 INVALID_INPUT)을 직접 확인하기 위한 스위치
  const [skipClientValidation, setSkipClientValidation] = useState(false)
  const [result, setResult] = useState<SignupResponse | null>(null)

  const inputRefs = useRef<Partial<Record<SignupField, HTMLInputElement | null>>>({})
  const clientErrors = validateSignup(form)

  function errorOf(field: SignupField): string | undefined {
    if (serverErrors[field] !== undefined) {
      return serverErrors[field]
    }
    if (skipClientValidation) {
      return undefined
    }
    return submitted || touched[field] ? clientErrors[field] : undefined
  }

  function handleChange(field: SignupField, value: string) {
    setForm((prev) => ({ ...prev, [field]: value }))
    // 고친 필드의 서버 에러는 지운다. 비밀번호를 바꾸면 불일치 에러도 의미가 없어진다.
    setServerErrors((prev) => {
      const next = { ...prev }
      delete next[field]
      if (field === 'password') {
        delete next.passwordConfirm
      }
      return next
    })
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (pending) {
      return
    }
    setSubmitted(true)
    setBanner(null)

    if (!skipClientValidation) {
      const field = firstErrorField(clientErrors)
      if (field !== undefined) {
        inputRefs.current[field]?.focus()
        return
      }
    }

    setPending(true)
    try {
      const response = await signup(toSignupRequest(form))
      setMemberId(String(response.memberId))
      setResult(response)
    } catch (error) {
      const feedback = toSignupErrorFeedback(error)
      setServerErrors(feedback.fieldErrors)
      setBanner(feedback.banner)
      if (feedback.focusField !== undefined) {
        inputRefs.current[feedback.focusField]?.focus()
      }
    } finally {
      setPending(false)
    }
  }

  function reset() {
    setForm(EMPTY_SIGNUP_FORM)
    setTouched({})
    setSubmitted(false)
    setServerErrors({})
    setBanner(null)
    setShowPassword(false)
    setResult(null)
  }

  if (result !== null) {
    return <SignupSuccess result={result} onReset={reset} />
  }

  return (
    <main className="page page--narrow">
      <h1 className="page__title">회원가입</h1>
      <p className="page__lead">가입하면 위탁계좌 1개가 함께 개설됩니다.</p>

      <form className="card form" onSubmit={handleSubmit} noValidate>
        {banner !== null && (
          <div role="alert" className="banner">
            <ErrorIcon />
            <span>{banner}</span>
          </div>
        )}

        {FIELD_SPECS.map((spec) => (
          <FormField
            key={spec.field}
            id={`signup-${spec.field}`}
            name={spec.field}
            label={spec.label}
            helper={spec.helper}
            placeholder={spec.placeholder}
            type={spec.secret && !showPassword ? 'password' : 'text'}
            autoComplete={spec.secret ? 'new-password' : spec.field === 'username' ? 'username' : 'nickname'}
            autoCapitalize="off"
            spellCheck={false}
            value={form[spec.field]}
            error={errorOf(spec.field)}
            warning={spec.field === 'username' && containsHangul(form.username) ? '한글 입력 상태예요. 한/영 키를 눌러 주세요.' : undefined}
            inputRef={(element) => {
              inputRefs.current[spec.field] = element
            }}
            onChange={(event) => handleChange(spec.field, event.target.value)}
            onBlur={() => setTouched((prev) => ({ ...prev, [spec.field]: true }))}
            trailing={
              spec.field === 'password' ? (
                <button
                  type="button"
                  className="button button--secondary field__toggle"
                  aria-pressed={showPassword}
                  onClick={() => setShowPassword((prev) => !prev)}
                >
                  {showPassword ? '숨기기' : '보기'}
                </button>
              ) : undefined
            }
          />
        ))}

        <button type="submit" className="button button--primary button--block" disabled={pending} aria-busy={pending}>
          {pending ? '가입 중…' : '가입하기'}
        </button>
      </form>

      <p className="page__footnote">로그인 기능은 준비 중입니다. 시연할 때는 상단 X-Member-Id로 회원을 전환하세요.</p>

      {import.meta.env.DEV && (
        <label className="dev-toggle">
          <input
            type="checkbox"
            checked={skipClientValidation}
            onChange={(event) => setSkipClientValidation(event.target.checked)}
          />
          <span>클라이언트 검증 끄기 (서버 검증 확인용, 개발 모드에서만 보임)</span>
        </label>
      )}
    </main>
  )
}

function SignupSuccess({ result, onReset }: { result: SignupResponse; onReset: () => void }) {
  const headingRef = useRef<HTMLHeadingElement>(null)

  useEffect(() => {
    headingRef.current?.focus()
  }, [])

  return (
    <main className="page page--narrow">
      <section className="card success" aria-labelledby="signup-success-title">
        <h1 id="signup-success-title" className="success__title" ref={headingRef} tabIndex={-1}>
          {result.nickname}님, 가입이 완료됐어요
        </h1>
        <dl className="summary">
          <div>
            <dt>아이디</dt>
            <dd className="mono">{result.username}</dd>
          </div>
          <div>
            <dt>회원번호</dt>
            <dd className="mono">{result.memberId}</dd>
          </div>
          <div className="summary__wide">
            <dt>위탁계좌</dt>
            <dd className="mono summary__account">{formatAccountNumber(result.accountNumber)}</dd>
          </div>
        </dl>
        {import.meta.env.DEV && (
          <p className="success__note">상단 X-Member-Id가 {result.memberId}번 회원으로 바뀌었어요.</p>
        )}
        <button type="button" className="button button--secondary button--block" onClick={onReset}>
          다른 회원 가입하기
        </button>
      </section>
    </main>
  )
}
