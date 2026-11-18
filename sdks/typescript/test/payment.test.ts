import { describe, expect, test } from "bun:test"
import { isPaymentStatus, isTerminalStatus, Money, PAYMENT_STATUSES } from "../src/index"
import { toPayment, toProvider, toWirePaymentRequest } from "../src/protocol/mapping"
import { wirePayment } from "../src/testing"

describe("Payment", () => {
    test("is read from the wire into merchant terms", () => {
        const payment = toPayment(
            wirePayment({
                payer: { phone_number: "+50934567890" },
                next_action: {
                    type: "redirect",
                    redirect_url: "https://pay.example/checkout/1",
                    expires_at: "2026-11-15T12:15:00Z",
                },
                metadata: { cart: "42" },
            })
        )
        expect(payment.amount.equals(Money.htg(1250))).toBe(true)
        expect(payment.payerPhoneNumber).toBe("+50934567890")
        expect(payment.createdAt).toEqual(new Date("2026-11-15T12:00:00Z"))
        expect(payment.requiresRedirect).toBe(true)
        expect(payment.redirectUrl).toBe("https://pay.example/checkout/1")
        expect(payment.metadata).toEqual({ cart: "42" })
        expect(Object.isFrozen(payment)).toBe(true)
    })

    test("knows when it is done", () => {
        expect(toPayment(wirePayment()).isPending).toBe(true)
        const succeeded = toPayment(
            wirePayment({ status: "succeeded", completed_at: "2026-11-15T12:01:00Z" })
        )
        expect(succeeded.isSuccessful && succeeded.isTerminal).toBe(true)
        expect(succeeded.requiresRedirect).toBe(false)
        expect(
            toPayment(wirePayment({ next_action: { type: "payer_approval" } })).awaitsPayerApproval
        ).toBe(true)
    })

    test("has no next action when the wire has none", () => {
        expect(toPayment(wirePayment({ next_action: null })).nextAction).toEqual({ type: "none" })
    })
})

describe("PaymentRequest", () => {
    test("is written to the wire without empty fields", () => {
        const wire = toWirePaymentRequest({
            reference: "INV-184",
            amount: Money.htg(1250),
            provider: "moncash",
            payerPhoneNumber: "+50934567890",
            expiresAt: new Date("2026-11-15T13:00:00Z"),
        })
        expect(wire).toEqual({
            reference: "INV-184",
            amount: { amount: 125000, currency: "HTG" },
            provider: "moncash",
            payer: { phone_number: "+50934567890" },
            expires_at: "2026-11-15T13:00:00.000Z",
        })
    })

    test("is checked before it leaves", () => {
        const valid = { reference: "INV-184", amount: Money.htg(1), provider: "moncash" }
        expect(() => toWirePaymentRequest({ ...valid, reference: " " })).toThrow("reference")
        expect(() => toWirePaymentRequest({ ...valid, amount: Money.centimes(0) })).toThrow(
            "amount"
        )
        expect(() => toWirePaymentRequest({ ...valid, payerPhoneNumber: "34567890" })).toThrow(
            "E.164"
        )
    })
})

describe("Provider", () => {
    const provider = toProvider({
        id: "moncash",
        display_name: "MonCash",
        capabilities: ["payments.redirect", "payments.statement"],
        payments: {
            next_actions: ["redirect"],
            payer_required: false,
            return_url_required: true,
            expiry_guaranteed: true,
            limits: {
                min_amount: { amount: 100, currency: "HTG" },
                max_amount: { amount: 7_500_000, currency: "HTG" },
            },
            locales: ["fr", "ht"],
        },
    })

    test("answers what it supports and accepts", () => {
        expect(provider.displayName).toBe("MonCash")
        expect(provider.supports("payments.statement")).toBe(true)
        expect(provider.accepts(Money.htg(1250))).toBe(true)
        expect(provider.accepts(Money.htg(0.5))).toBe(false)
        expect(provider.accepts(Money.htg(80_000))).toBe(false)
    })
})

describe("payment statuses", () => {
    test("tell a terminal status from pending and an unknown value from a status", () => {
        expect(PAYMENT_STATUSES.filter(isTerminalStatus)).toEqual([
            "succeeded",
            "failed",
            "expired",
            "canceled",
        ])
        expect(isPaymentStatus("pending")).toBe(true)
        expect(isPaymentStatus("refunded")).toBe(false)
    })
})
