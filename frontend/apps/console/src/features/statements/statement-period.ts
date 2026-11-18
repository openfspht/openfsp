export const MAX_RANGE_DAYS = 31

export const PERIOD_MODES = ["day", "range"] as const

export type PeriodMode = (typeof PERIOD_MODES)[number]

export type PeriodProblem = "incomplete" | "reversed" | "too-long"

export type Period =
    | { params: { business_date: string } | { from: string; to: string }; problem?: undefined }
    | { params?: undefined; problem: PeriodProblem }

const DATE = /^\d{4}-\d{2}-\d{2}$/
const MILLISECONDS_PER_DAY = 86_400_000

const isDate = (value: string) => DATE.test(value) && !Number.isNaN(Date.parse(value))

export const isPeriodMode = (value: string): value is PeriodMode =>
    (PERIOD_MODES as readonly string[]).includes(value)

export function dayPeriod(day: string): Period {
    return isDate(day) ? { params: { business_date: day } } : { problem: "incomplete" }
}

export function rangePeriod(from: string, to: string): Period {
    if (!isDate(from) || !isDate(to)) {
        return { problem: "incomplete" }
    }
    const days = (Date.parse(to) - Date.parse(from)) / MILLISECONDS_PER_DAY + 1
    if (days < 1) {
        return { problem: "reversed" }
    }
    if (days > MAX_RANGE_DAYS) {
        return { problem: "too-long" }
    }
    return { params: { from, to } }
}

export function localToday(): string {
    const now = new Date()
    const pad = (value: number) => String(value).padStart(2, "0")
    return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`
}
