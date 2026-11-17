import type { Money } from "./money.js"
import type { Locale, NextAction } from "./payment.js"

export interface AmountLimits {
    readonly min?: Money
    readonly max?: Money
    readonly step?: Money
}

export interface ProviderFields {
    readonly id: string
    readonly displayName: string
    readonly logoUrl?: string
    readonly capabilities: readonly string[]
    readonly nextActions: readonly NextAction["type"][]
    readonly payerRequired: boolean
    readonly returnUrlRequired: boolean
    readonly expiryGuaranteed: boolean
    readonly limits: AmountLimits
    readonly locales: readonly Locale[]
}

export interface Provider extends ProviderFields {}

export class Provider {
    constructor(fields: ProviderFields) {
        Object.assign(this, fields)
        Object.freeze(this)
    }

    supports(capability: string): boolean {
        return this.capabilities.includes(capability)
    }

    accepts(amount: Money): boolean {
        const { min, max, step } = this.limits
        if (min !== undefined && amount.centimes < min.centimes) {
            return false
        }
        if (max !== undefined && amount.centimes > max.centimes) {
            return false
        }
        return step === undefined || step.centimes === 0 || amount.centimes % step.centimes === 0
    }
}
