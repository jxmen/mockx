export type FieldErrorItem = {
  field: string
  detail: string
}

/** RFC 9457 Problem Details + 확장 필드(code, field, errors). docs/db-design-member.md 6장 */
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
}

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json, application/problem+json' }
  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }

  let response: Response
  try {
    response = await fetch(path, {
      method: options.method ?? 'GET',
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
    })
  } catch (cause) {
    throw new NetworkError({ cause })
  }

  if (!response.ok) {
    throw new ApiError(response.status, await readProblem(response))
  }
  return (await response.json()) as T
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
