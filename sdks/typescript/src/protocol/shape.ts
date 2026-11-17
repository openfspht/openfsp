import { ApiError } from "../errors.js"

export function requireShape<T extends object>(value: T, fields: readonly (keyof T & string)[]): T {
    const missing = fields.filter(
        (field) =>
            (value as Record<string, unknown>)[field] === undefined ||
            (value as Record<string, unknown>)[field] === null
    )
    if (missing.length > 0) {
        throw new ApiError({
            status: 200,
            code: "unexpected-response",
            title: `The response is missing ${missing.join(", ")}`,
            retryable: false,
            effect: "unknown",
        })
    }
    return value
}
