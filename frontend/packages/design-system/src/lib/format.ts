import type { Money } from "@workspace/types"

const cache = new Map<string, Intl.NumberFormat | Intl.DateTimeFormat>()

function cached<T extends Intl.NumberFormat | Intl.DateTimeFormat>(key: string, make: () => T): T {
  const existing = cache.get(key)
  if (existing) return existing as T
  const made = make()
  cache.set(key, made)
  return made
}

const DEFAULT_LOCALE = "en-GB"

export const formatMoney = (money: Money, locale = DEFAULT_LOCALE) =>
  cached(`money-${locale}`, () => new Intl.NumberFormat(locale, { style: "currency", currency: "HTG", currencyDisplay: "code", minimumFractionDigits: 2 })).format(
    money.amount / 100
  )

export const formatDateTime = (timestamp: string, locale = DEFAULT_LOCALE) =>
  cached(`datetime-${locale}`, () => new Intl.DateTimeFormat(locale, { dateStyle: "medium", timeStyle: "medium" })).format(new Date(timestamp))

export const formatDate = (isoDate: string, locale = DEFAULT_LOCALE) => {
  const [year, month, day] = isoDate.split("-").map(Number)
  return cached(`date-${locale}`, () => new Intl.DateTimeFormat(locale, { dateStyle: "medium" })).format(new Date(year, month - 1, day))
}
