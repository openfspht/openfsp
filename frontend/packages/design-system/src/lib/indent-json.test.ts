import { describe, expect, test } from "bun:test"

import { indentJson } from "./indent-json"

describe("indent json", () => {
    test("numbers keep the digits that were stored", () => {
        expect(indentJson('{"id":9007199254740993,"amount":12.50}')).toBe(
            '{\n  "id": 9007199254740993,\n  "amount": 12.50\n}'
        )
    })

    test("the layout matches JSON.stringify for ordinary documents", () => {
        const text = '{"a":[1,{"b":"x, \\"y\\": {z}"}],"c":{},"d":[]}'
        expect(indentJson(text)).toBe(JSON.stringify(JSON.parse(text), null, 2))
    })

    test("text that is not json is refused", () => {
        expect(() => indentJson("[REDACTED]")).toThrow()
    })
})
