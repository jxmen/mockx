import type { InputHTMLAttributes, ReactNode, Ref } from 'react'

type FormFieldProps = {
  id: string
  label: string
  /** 에러가 없을 때 보여 줄 안내 문구 */
  helper: string
  error?: string
  /** 입력칸 아래, 안내 문구 위에 붙는 경고(한글 입력 상태 등) */
  warning?: string
  /** 입력칸 오른쪽 버튼(비밀번호 보기 등) */
  trailing?: ReactNode
  inputRef?: Ref<HTMLInputElement>
} & Omit<InputHTMLAttributes<HTMLInputElement>, 'id'>

export function FormField({ id, label, helper, error, warning, trailing, inputRef, ...inputProps }: FormFieldProps) {
  const hintId = `${id}-hint`
  const hasError = error !== undefined

  return (
    <div className="field">
      <label htmlFor={id} className="field__label">
        {label}
      </label>
      <div className="field__row">
        <input
          {...inputProps}
          id={id}
          ref={inputRef}
          className={hasError ? 'field__input field__input--error' : 'field__input'}
          aria-invalid={hasError}
          aria-describedby={hintId}
        />
        {trailing}
      </div>
      {warning && (
        <p className="field__warning">
          <KeyboardIcon />
          <span>{warning}</span>
        </p>
      )}
      <p id={hintId} className={hasError ? 'field__hint field__hint--error' : 'field__hint'}>
        {hasError && <ErrorIcon />}
        <span>{hasError ? error : helper}</span>
      </p>
    </div>
  )
}

export function ErrorIcon() {
  return (
    <svg className="icon" aria-hidden="true" width="16" height="16" viewBox="0 0 16 16">
      <circle cx="8" cy="8" r="6.5" />
      <path d="M8 4.5v4.5M8 11v0.5" />
    </svg>
  )
}

function KeyboardIcon() {
  return (
    <svg className="icon" aria-hidden="true" width="16" height="16" viewBox="0 0 16 16">
      <rect x="1.5" y="3.5" width="13" height="9" rx="1.5" />
      <path d="M4 6.5h1M7 6.5h2M11 6.5h1M5 9.5h6" />
    </svg>
  )
}
