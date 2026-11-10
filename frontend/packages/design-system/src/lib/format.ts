import type { Money } from "@workspace/types"

const amountFormat = new Intl.NumberFormat("en-US", {
  style: "currency",
  currency: "HTG",
  currencyDisplay: "code",
  minimumFractionDigits: 2,
})

const timeFormat = new Intl.DateTimeFormat("en-GB", {
  hour: "2-digit",
  minute: "2-digit",
  second: "2-digit",
  fractionalSecondDigits: 3,
})

const dateTimeFormat = new Intl.DateTimeFormat("en-GB", { dateStyle: "medium", timeStyle: "medium" })

export const formatMoney = (money: Money) => amountFormat.format(money.amount / 100)

export const formatTime = (timestamp: string) => timeFormat.format(new Date(timestamp))

export const formatDateTime = (timestamp: string) => dateTimeFormat.format(new Date(timestamp))
