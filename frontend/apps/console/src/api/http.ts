export class UnauthenticatedError extends Error {}

export async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    ...init,
    credentials: "same-origin",
    headers: { "Content-Type": "application/json", ...init?.headers },
  })
  if (response.status === 401) throw new UnauthenticatedError()
  if (!response.ok)
    throw new Error(
      `${init?.method ?? "GET"} ${path} answered ${response.status}`
    )
  return response.status === 204
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
