import type { FieldError, Problem } from "@workspace/types"

export type ProblemBody = Partial<Omit<Problem, "errors">> & { errors?: Partial<FieldError>[] }

export type QueryParams = Record<string, string | number | readonly string[]>

export class HttpError extends Error {
    readonly status: number
    readonly problem?: ProblemBody
    readonly reason?: string

    constructor(request: string, status: number, problem?: ProblemBody) {
        const reason = problemReason(problem)
        super(reason ?? `${request} answered ${status}`)
        this.name = "HttpError"
        this.status = status
        this.problem = problem
        this.reason = reason
    }
}

export class UnauthenticatedError extends HttpError {
    constructor(request: string, problem?: ProblemBody) {
        super(request, 401, problem)
        this.name = "UnauthenticatedError"
    }
}

export class NetworkError extends Error {
    constructor(request: string, cause: unknown) {
        super(`${request} could not reach the server`, { cause })
        this.name = "NetworkError"
    }
}

function problemReason(problem?: ProblemBody): string | undefined {
    const error = problem?.errors?.[0]
    const reason = error?.detail ?? error?.code
    if (reason) {
        return error?.field ? `${error.field}: ${reason}` : reason
    }
    return problem?.detail ?? problem?.title
}

async function problemOf(response: Response): Promise<ProblemBody | undefined> {
    const body: unknown = await response.json().catch(() => undefined)
    return typeof body === "object" && body !== null ? (body as ProblemBody) : undefined
}

async function send(path: string, init?: RequestInit): Promise<Response> {
    const label = `${init?.method ?? "GET"} ${path}`
    const response = await fetch(path, {
        ...init,
        credentials: "same-origin",
        headers: { "Content-Type": "application/json", ...init?.headers },
    }).catch((cause: unknown) => {
        throw new NetworkError(label, cause)
    })
    if (response.status === 401) {
        throw new UnauthenticatedError(label, await problemOf(response))
    }
    if (!response.ok) {
        throw new HttpError(label, response.status, await problemOf(response))
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

export const query = (params: QueryParams) =>
    new URLSearchParams(
        Object.entries(params).flatMap(([key, value]) =>
            typeof value === "object"
                ? value.map((item) => [key, item])
                : value === ""
                  ? []
                  : [[key, String(value)]]
        )
    ).toString()

export const post = (body?: unknown): RequestInit => ({
    method: "POST",
    body: body === undefined ? undefined : JSON.stringify(body),
})

export const put = (body: unknown): RequestInit => ({
    method: "PUT",
    body: JSON.stringify(body),
})

export const remove = (): RequestInit => ({ method: "DELETE" })
