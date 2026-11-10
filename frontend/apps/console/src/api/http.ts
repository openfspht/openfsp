export class UnauthenticatedError extends Error {}

export class ProblemError extends Error {
  readonly status: number

  constructor(message: string, status: number) {
    super(message)
    this.status = status
  }
}

interface ProblemBody {
  title?: string
  detail?: string
  errors?: { field?: string; message?: string }[]
}

async function problemOf(response: Response): Promise<string | undefined> {
  const body = (await response.json().catch(() => undefined)) as ProblemBody | undefined
  const error = body?.errors?.[0]
  if (error?.message) return error.field ? `${error.field}: ${error.message}` : error.message
  return body?.detail ?? body?.title
}

export async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    ...init,
    credentials: "same-origin",
    headers: { "Content-Type": "application/json", ...init?.headers },
  })
  if (response.status === 401) throw new UnauthenticatedError()
  if (!response.ok)
    throw new ProblemError((await problemOf(response)) ?? `${init?.method ?? "GET"} ${path} answered ${response.status}`, response.status)
  return response.status === 204 || response.status === 201
    ? (undefined as T)
    : ((await response.json()) as T)
}

export const post = (body?: unknown): RequestInit => ({
  method: "POST",
  body: body === undefined ? undefined : JSON.stringify(body),
})

export const put = (body: unknown): RequestInit => ({
  method: "PUT",
  body: JSON.stringify(body),
})

export const remove = (): RequestInit => ({ method: "DELETE" })
