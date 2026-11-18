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
    errors?: { field?: string; code?: string; detail?: string }[]
}

async function problemOf(response: Response): Promise<string | undefined> {
    const body = (await response.json().catch(() => undefined)) as ProblemBody | undefined
    const error = body?.errors?.[0]
    const reason = error?.detail ?? error?.code
    if (reason) {
        return error?.field ? `${error.field}: ${reason}` : reason
    }
    return body?.detail ?? body?.title
}

async function send(path: string, init?: RequestInit): Promise<Response> {
    const response = await fetch(path, {
        ...init,
        credentials: "same-origin",
        headers: { "Content-Type": "application/json", ...init?.headers },
    })
    if (response.status === 401) {
        throw new UnauthenticatedError()
    }
    if (!response.ok) {
        throw new ProblemError(
            (await problemOf(response)) ??
                `${init?.method ?? "GET"} ${path} answered ${response.status}`,
            response.status
        )
    }
    return response
}

export async function request<T>(path: string, init?: RequestInit): Promise<T> {
    const body = await (await send(path, init)).text()
    return (body ? JSON.parse(body) : undefined) as T
}

const ATTACHMENT_NAME = /filename="?([^";]+)"?/

export async function download(path: string, fallbackName: string): Promise<void> {
    const response = await send(path)
    const name =
        ATTACHMENT_NAME.exec(response.headers.get("Content-Disposition") ?? "")?.[1] ?? fallbackName
    const url = URL.createObjectURL(await response.blob())
    const link = document.createElement("a")
    link.href = url
    link.download = name
    document.body.append(link)
    link.click()
    link.remove()
    setTimeout(() => URL.revokeObjectURL(url))
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
