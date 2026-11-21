export type FieldErrorItem = {
  field: string
  detail: string
}

/** RFC 9457 Problem Details + 확장 필드(code, field, errors). docs/design/member.md 6장 */
export type ProblemDetail = {
  type?: string
  title?: string
  status: number
  detail?: string
  instance?: string
  code?: string
  field?: string
  errors?: FieldErrorItem[]
}

/** 서버가 4xx, 5xx를 응답했다. 본문이 Problem Details가 아니면 problem은 null이다. */
export class ApiError extends Error {
  readonly status: number
  readonly problem: ProblemDetail | null

  constructor(status: number, problem: ProblemDetail | null) {
    super(problem?.detail ?? `HTTP ${status}`)
    this.name = 'ApiError'
    this.status = status
    this.problem = problem
  }
}

/** 응답을 받지 못했다(서버 꺼짐, 네트워크 끊김 등). */
export class NetworkError extends Error {
  constructor(options?: ErrorOptions) {
    super('서버에 연결할 수 없습니다.', options)
    this.name = 'NetworkError'
  }
}

type RequestOptions = {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  body?: unknown
  /** 개발용 인증 스텁. 값이 있으면 X-Member-Id 헤더로 보낸다. docs/design/member.md 5장 */
  memberId?: string | null
  signal?: AbortSignal
  /** 요청마다 붙일 헤더(예: Idempotency-Key). 기본 헤더(Accept, Content-Type, X-Member-Id)를 만든 뒤 합친다 */
  headers?: Record<string, string>
}

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  return (await apiRequestWithHeaders<T>(path, options)).data
}

/** apiRequest와 같고, 응답 헤더(예: Idempotent-Replayed)도 함께 돌려준다 */
export async function apiRequestWithHeaders<T>(
  path: string,
  options: RequestOptions = {},
): Promise<{ data: T; headers: Headers }> {
  const headers: Record<string, string> = { Accept: 'application/json, application/problem+json' }
  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }
  if (options.memberId) {
    headers['X-Member-Id'] = options.memberId
  }
  Object.assign(headers, options.headers)

  let response: Response
  try {
    response = await fetch(path, {
      method: options.method ?? 'GET',
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
      signal: options.signal,
    })
  } catch (cause) {
    // 호출한 쪽이 취소한 요청은 네트워크 오류가 아니므로 그대로 던진다
    if (options.signal?.aborted) {
      throw cause
    }
    throw new NetworkError({ cause })
  }

  if (!response.ok) {
    throw new ApiError(response.status, await readProblem(response))
  }
  return { data: (await response.json()) as T, headers: response.headers }
}

async function readProblem(response: Response): Promise<ProblemDetail | null> {
  const contentType = response.headers.get('Content-Type') ?? ''
  if (!contentType.includes('json')) {
    return null
  }
  try {
    return (await response.json()) as ProblemDetail
  } catch {
    return null
  }
}
