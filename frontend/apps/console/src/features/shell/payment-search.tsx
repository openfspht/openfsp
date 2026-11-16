import { Input } from "@workspace/design-system"
import { useState } from "react"
import { useNavigate } from "react-router"

import { gatewayAdmin } from "@/api/gateway-admin"
import { useI18n } from "@/i18n/i18n-provider"

export function PaymentSearch() {
  const { t } = useI18n()
  const navigate = useNavigate()
  const [query, setQuery] = useState("")
  const [missed, setMissed] = useState(false)
  return (
    <form
      role="search"
      className="hidden md:block"
      onSubmit={(event) => {
        event.preventDefault()
        void gatewayAdmin.payments({ q: query.trim(), size: 1 }).then((result) => {
          const found = result.data[0]
          setMissed(!found)
          if (found) navigate(`/activity?payment=${found.id}`)
        })
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
