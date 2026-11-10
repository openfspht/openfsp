import type { AttestedStatus } from "@workspace/types"

import { Button } from "../atoms"
import { useCopy } from "../copy"

const actions: { status: "succeeded" | "failed" | "expired"; variant: "default" | "outline" | "destructive" }[] = [
  { status: "succeeded", variant: "default" },
  { status: "failed", variant: "destructive" },
  { status: "expired", variant: "outline" },
]

export function AttestationActions({ disabled, onAttest }: { disabled?: boolean; onAttest: (status: AttestedStatus) => void }) {
  const copy = useCopy()
  return (
    <div className="flex flex-wrap gap-2">
      {actions.map(({ status, variant }) => (
        <Button key={status} size="sm" variant={variant} disabled={disabled} onClick={() => onAttest(status)}>
          {copy.attestation[status]}
        </Button>
      ))}
    </div>
  )
}
