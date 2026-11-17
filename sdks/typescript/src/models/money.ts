import { InvalidRequestError } from "../errors.js"

const CENTIMES_PER_GOURDE = 100
const GOURDES = /^-?\d+(\.\d{1,2})?$/

export class Money {
    readonly centimes: number
    readonly currency = "HTG"

    private constructor(centimes: number) {
        this.centimes = centimes
        Object.freeze(this)
    }

    static htg(gourdes: number | string): Money {
        const text = typeof gourdes === "number" ? String(gourdes) : gourdes.trim()
        if (!GOURDES.test(text)) {
            throw new InvalidRequestError(
                `${gourdes} is not an amount in gourdes with at most two decimals`
            )
        }
        const [whole, fraction = ""] = text.replace("-", "").split(".")
        const centimes = Number(whole) * CENTIMES_PER_GOURDE + Number(fraction.padEnd(2, "0"))
        return Money.centimes(text.startsWith("-") ? -centimes : centimes)
    }

    static centimes(centimes: number): Money {
        if (!Number.isSafeInteger(centimes)) {
            throw new InvalidRequestError(`${centimes} is not a whole number of centimes`)
        }
        return new Money(centimes)
    }

    get gourdes(): string {
        const sign = this.centimes < 0 ? "-" : ""
        const absolute = Math.abs(this.centimes)
        return `${sign}${Math.trunc(absolute / CENTIMES_PER_GOURDE)}.${String(absolute % CENTIMES_PER_GOURDE).padStart(2, "0")}`
    }

    get isPositive(): boolean {
        return this.centimes > 0
    }

    equals(other: Money): boolean {
        return this.centimes === other.centimes && this.currency === other.currency
    }

    toString(): string {
        return `${this.gourdes} ${this.currency}`
    }

    toJSON(): { amount: number; currency: "HTG" } {
        return { amount: this.centimes, currency: this.currency }
    }
}
