import { Button } from "@workspace/design-system"
import type { AttestedStatus } from "@workspace/types"

import { useI18n } from "@/i18n/i18n-provider"

const actions: {
  status: "succeeded" | "failed" | "expired"
  variant: "default" | "outline" | "destructive"
}[] = [
  { status: "succeeded", variant: "default" },
  { status: "failed", variant: "destructive" },
  { status: "expired", variant: "outline" },
]

export function AttestationActions({
  disabled,
  onAttest,
}: {
  disabled?: boolean
  onAttest: (status: AttestedStatus) => void
}) {
  const copy = useI18n().t
  return (
    <div className="flex flex-wrap gap-2">
      {actions.map(({ status, variant }) => (
        <Button
          key={status}
          size="sm"
          variant={variant}
          disabled={disabled}
          onClick={() => onAttest(status)}
        >
          {copy.attestation[status]}
        </Button>
      ))}
    </div>
  )
}
