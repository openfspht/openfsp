import { describe, expect, test } from "bun:test"

import { formatPhoneNumber, toE164 } from "./phone"

describe("toE164", () => {
  test("adds the Haitian code to an eight-digit local number", () => {
    expect(toE164("34567890")).toBe("+50934567890")
    expect(toE164("3456 7890")).toBe("+50934567890")
    expect(toE164("3456-7890")).toBe("+50934567890")
  })

  test("keeps an E.164 number and completes a national one", () => {
    expect(toE164("+509 34 56 78 90")).toBe("+50934567890")
    expect(toE164("50934567890")).toBe("+50934567890")
    expect(toE164("+15145550100")).toBe("+15145550100")
  })

  test("leaves anything else for the gateway to reject", () => {
    expect(toE164("1234")).toBe("1234")
    expect(toE164("050934567890")).toBe("050934567890")
  })
})

describe("formatPhoneNumber", () => {
  test("writes a Haitian number as +509 40 00 0000", () => {
    expect(formatPhoneNumber("40000000")).toBe("+509 40 00 0000")
    expect(formatPhoneNumber("+50940000000")).toBe("+509 40 00 0000")
    expect(formatPhoneNumber("+509 40 00 0000")).toBe("+509 40 00 0000")
  })

  test("leaves other numbers in E.164", () => {
    expect(formatPhoneNumber("+15145550100")).toBe("+15145550100")
  })
})
