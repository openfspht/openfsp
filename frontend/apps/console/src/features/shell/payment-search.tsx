import { Input } from "@workspace/design-system"
import type { Payment } from "@workspace/types"
import { useState } from "react"
import { useNavigate } from "react-router"

import { usePayments } from "@/features/payments/use-payment"
import { useI18n } from "@/i18n/i18n-provider"

const CENTS = 100

function matches(payment: Payment, query: string): boolean {
  const needle = query.trim().toLowerCase()
  if (!needle) return false
  const amount = Number(needle.replace(",", "."))
  return (
    payment.id.toLowerCase() === needle ||
    payment.reference.toLowerCase().includes(needle) ||
    (payment.provider_reference?.toLowerCase().includes(needle) ?? false) ||
    (!Number.isNaN(amount) && Math.round(amount * CENTS) === payment.amount.amount)
  )
}

export function PaymentSearch() {
  const { t } = useI18n()
  const navigate = useNavigate()
  const { list } = usePayments()
  const [query, setQuery] = useState("")
  const [missed, setMissed] = useState(false)
  return (
    <form
      role="search"
      className="hidden md:block"
      onSubmit={(event) => {
        event.preventDefault()
        const found = list.find((payment) => matches(payment, query))
        setMissed(!found)
        if (found) navigate(`/activity?payment=${found.id}`)
      }}
    >
      <Input
        type="search"
        aria-label={t.activity.searchLabel}
        aria-invalid={missed || undefined}
        title={missed ? t.activity.noMatch : undefined}
        placeholder={missed ? t.activity.noMatch : t.activity.search}
        value={query}
        onChange={(event) => {
          setQuery(event.target.value)
          setMissed(false)
        }}
        className="w-72"
      />
    </form>
  )
}
