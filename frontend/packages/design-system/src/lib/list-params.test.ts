import { describe, expect, test } from "bun:test"

import { applyListChanges, readListParams } from "./list-params"

describe("list params", () => {
    test("reads the page, the filters and the sort from the address", () => {
        const read = readListParams(
            new URLSearchParams("page=2&status=pending&sort=amount&order=asc"),
            ["status", "q"] as const
        )
        expect(read.page).toBe(2)
        expect(read.values).toEqual({ status: "pending", q: "" })
        expect(read.sort).toEqual({ key: "amount", ascending: true })
        expect(read.sortParams).toEqual({ sort: "amount", order: "asc" })
    })

    test("an invalid or negative page falls back to the first page", () => {
        expect(readListParams(new URLSearchParams("page=abc"), []).page).toBe(0)
        expect(readListParams(new URLSearchParams("page=-3"), []).page).toBe(0)
    })

    test("without a sort, uses the default and sends no sort parameter", () => {
        const read = readListParams(new URLSearchParams(), [], { key: "", ascending: false })
        expect(read.sort).toEqual({ key: "", ascending: false })
        expect(read.sortParams).toEqual({})
    })

    test("changing a filter returns to the first page and drops empty values", () => {
        const next = applyListChanges(new URLSearchParams("page=3&status=pending&q=abc"), {
            status: "failed",
            q: "",
        })
        expect(next.toString()).toBe("status=failed")
    })

    test("changing the page keeps the filters", () => {
        const next = applyListChanges(new URLSearchParams("status=pending"), { page: "4" })
        expect(next.toString()).toBe("status=pending&page=4")
    })
})
