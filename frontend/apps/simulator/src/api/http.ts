export async function request<T>(path: string, init?: RequestInit): Promise<T> {
    const response = await fetch(path, {
        ...init,
        headers: { "Content-Type": "application/json", ...init?.headers },
    })
    if (!response.ok) {
        throw new Error(`${init?.method ?? "GET"} ${path} answered ${response.status}`)
    }
    return response.status === 204 ? (undefined as T) : ((await response.json()) as T)
}

export const post = (body?: unknown): RequestInit => ({
    method: "POST",
    body: body === undefined ? undefined : JSON.stringify(body),
})

export const put = (body: unknown): RequestInit => ({
    method: "PUT",
    body: JSON.stringify(body),
})
