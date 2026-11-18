import { describe, expect, test } from "bun:test"

import { HOME, safeNext } from "./next-path"

describe("next path after sign-in", () => {
    test("keeps a path of the console with its query and fragment", () => {
        expect(safeNext("/payments/pay_1")).toBe("/payments/pay_1")
        expect(safeNext("/activity?filter=callbacks#top")).toBe("/activity?filter=callbacks#top")
    })

    test("falls back to the home page without a next path", () => {
        expect(safeNext(null)).toBe(HOME)
        expect(safeNext("")).toBe(HOME)
    })

    test("refuses any path that leaves the console", () => {
        ;[
            "https://evil.example",
            "//evil.example",
            "/\\evil.example",
            "\\\\evil.example",
            "/\t/evil.example",
            "/\n/evil.example",
            "javascript:alert(1)",
            "payments",
        ].forEach((value) => expect(safeNext(value)).toBe(HOME))
    })
})
