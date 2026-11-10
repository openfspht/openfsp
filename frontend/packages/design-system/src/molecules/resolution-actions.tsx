import type { Outcome } from "@workspace/types"

import { Button } from "../atoms"

export interface Resolution {
  outcome: Outcome
  reason?: string
}

const actions: { label: string; resolution: Resolution; variant: "default" | "outline" | "destructive" }[] = [
  { label: "Succeed", resolution: { outcome: "SUCCESSFUL" }, variant: "default" },
  { label: "Decline", resolution: { outcome: "FAILED", reason: "DECLINED" }, variant: "destructive" },
  { label: "Expire", resolution: { outcome: "EXPIRED" }, variant: "outline" },
]

export function ResolutionActions({ disabled, onResolve }: { disabled?: boolean; onResolve: (resolution: Resolution) => void }) {
  return (
    <div className="flex gap-2">
      {actions.map(({ label, resolution, variant }) => (
        <Button key={label} size="sm" variant={variant} disabled={disabled} onClick={() => onResolve(resolution)}>
          {label}
        </Button>
      ))}
    </div>
  )
}
