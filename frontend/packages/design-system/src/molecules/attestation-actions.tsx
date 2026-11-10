import type { AttestedStatus } from "@workspace/types"

import { Button } from "../atoms"

const actions: { label: string; status: AttestedStatus; variant: "default" | "outline" | "destructive" }[] = [
  { label: "Attest succeeded", status: "succeeded", variant: "default" },
  { label: "Attest failed", status: "failed", variant: "destructive" },
  { label: "Attest expired", status: "expired", variant: "outline" },
]

export function AttestationActions({ disabled, onAttest }: { disabled?: boolean; onAttest: (status: AttestedStatus) => void }) {
  return (
    <div className="flex flex-wrap gap-2">
      {actions.map(({ label, status, variant }) => (
        <Button key={status} size="sm" variant={variant} disabled={disabled} onClick={() => onAttest(status)}>
          {label}
        </Button>
      ))}
    </div>
  )
}
