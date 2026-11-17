type Settings = {
    dsn?: string
    environment?: string
    release?: string
    tracesSampleRate?: string
}

type ErrorHandler = (error: unknown, info: { componentStack?: string }) => void

const SETTINGS_PATH = "/monitoring.json"

let handler: ErrorHandler | undefined

const report: ErrorHandler = (error, info) => handler?.(error, info)

export const rootErrorHandlers = {
    onUncaughtError: report,
    onCaughtError: report,
    onRecoverableError: report,
}

async function settings(): Promise<Settings | undefined> {
    try {
        const response = await fetch(SETTINGS_PATH, { cache: "no-store" })
        return response.ok ? ((await response.json()) as Settings) : undefined
    } catch {
        return undefined
    }
}

export async function startMonitoring(service: string): Promise<void> {
    const found = await settings()
    if (!found?.dsn) {
        return
    }
    const Sentry = await import("@sentry/react")
    Sentry.init({
        dsn: found.dsn,
        environment: found.environment || undefined,
        release: found.release || undefined,
        dataCollection: { userInfo: false, httpBodies: [] },
        integrations: [Sentry.browserTracingIntegration()],
        tracesSampleRate: Number(found.tracesSampleRate) || 0,
        initialScope: { tags: { service } },
    })
    handler = Sentry.reactErrorHandler()
}
