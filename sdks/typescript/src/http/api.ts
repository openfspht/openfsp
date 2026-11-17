import type { OpenFspConfig } from "../config.js"
import { ApiError, OpenFspError } from "../errors.js"
import { parseObject, toApiError } from "../protocol/problem.js"
import { retryDelay } from "./retry.js"
import type { Transport } from "./transport.js"

export interface ApiCall {
    readonly method: "GET" | "POST"
    readonly path: string
    readonly query?: Readonly<Record<string, string>>
    readonly body?: unknown
    readonly idempotencyKey?: string
    readonly authenticated?: boolean
}

export interface Clock {
    now(): number
    sleep(milliseconds: number): Promise<void>
    random(): number
}

export const systemClock: Clock = {
    now: () => Date.now(),
    sleep: (milliseconds) => new Promise((resolve) => setTimeout(resolve, milliseconds)),
    random: () => Math.random(),
}

const USER_AGENT = "openfsp-sdk-typescript"
const ACCEPT = "application/json, application/problem+json"

export class Api {
    private readonly config: OpenFspConfig
    private readonly transport: Transport
    private readonly clock: Clock

    constructor(config: OpenFspConfig, transport: Transport, clock: Clock) {
        this.config = config
        this.transport = transport
        this.clock = clock
    }

    async call<T>(call: ApiCall): Promise<T> {
        for (let attempt = 1; ; attempt++) {
            try {
                return await this.attempt<T>(call)
            } catch (failure) {
                if (!(failure instanceof OpenFspError)) {
                    throw failure
                }
                const delay = retryDelay(
                    this.config.retryPolicy,
                    failure,
                    attempt,
                    this.clock.random()
                )
                if (delay === undefined) {
                    throw failure
                }
                await this.clock.sleep(delay)
            }
        }
    }

    private async attempt<T>(call: ApiCall): Promise<T> {
        const url = new URL(call.path, this.config.baseUrl)
        for (const [name, value] of Object.entries(call.query ?? {})) {
            url.searchParams.set(name, value)
        }
        const headers = new Headers({ Accept: ACCEPT, "User-Agent": USER_AGENT })
        if (call.authenticated !== false) {
            headers.set("Authorization", `Bearer ${this.config.apiKey}`)
        }
        if (call.idempotencyKey !== undefined) {
            headers.set("Idempotency-Key", call.idempotencyKey)
        }
        if (call.body !== undefined) {
            headers.set("Content-Type", "application/json")
        }

        const response = await this.transport.send({
            method: call.method,
            url,
            headers,
            body: call.body === undefined ? undefined : JSON.stringify(call.body),
            timeoutMs: this.config.timeoutMs,
        })
        if (response.status < 200 || response.status >= 300) {
            throw toApiError(response, this.clock.now())
        }
        const body = parseObject(response.body)
        if (body === undefined) {
            throw new ApiError({
                status: response.status,
                code: "unexpected-response",
                title: "The server answered with an unreadable body",
                retryable: false,
                effect: "unknown",
            })
        }
        return body as T
    }
}
