import { ApiError } from "../errors.js"

export function requireShape<T extends object>(value: T, fields: readonly (keyof T & string)[]): T {
    if (typeof value !== "object" || value === null || Array.isArray(value)) {
        throw unexpected("The response is not an object")
    }
    const missing = fields.filter(
        (field) =>
            (value as Record<string, unknown>)[field] === undefined ||
            (value as Record<string, unknown>)[field] === null
    )
    if (missing.length > 0) {
        throw unexpected(`The response is missing ${missing.join(", ")}`)
    }
    return value
}

export function requireArray<T>(value: readonly T[], name: string): readonly T[] {
    if (!Array.isArray(value)) {
        throw unexpected(`The response holds no ${name} list`)
    }
    return value
}

function unexpected(title: string): ApiError {
    return new ApiError({
        status: 200,
        code: "unexpected-response",
        title,
        retryable: false,
        effect: "unknown",
    })
}
