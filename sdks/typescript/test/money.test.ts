import { describe, expect, test } from "bun:test"
import { InvalidRequestError, Money } from "../src/index"

describe("Money", () => {
    test("reads gourdes and keeps centimes", () => {
        expect(Money.htg(1250).centimes).toBe(125000)
        expect(Money.htg("1250.5").centimes).toBe(125050)
        expect(Money.htg(0.07).centimes).toBe(7)
        expect(Money.centimes(125050).gourdes).toBe("1250.50")
    })

    test("refuses more than two decimals and fractional centimes", () => {
        expect(() => Money.htg(1.234)).toThrow(InvalidRequestError)
        expect(() => Money.htg("12,50")).toThrow(InvalidRequestError)
        expect(() => Money.centimes(1.5)).toThrow(InvalidRequestError)
    })

    test("compares, prints and serializes", () => {
        expect(Money.htg(5).equals(Money.centimes(500))).toBe(true)
        expect(String(Money.htg("1250.5"))).toBe("1250.50 HTG")
        expect(String(Money.centimes(-7))).toBe("-0.07 HTG")
        expect(JSON.stringify(Money.htg(5))).toBe('{"amount":500,"currency":"HTG"}')
    })

    test("is immutable", () => {
        expect(Object.isFrozen(Money.htg(1))).toBe(true)
    })
})
