import type { PaymentStatus } from "@workspace/types"
import { cn } from "@workspace/ui/lib/utils"

const tone: Record<PaymentStatus, string> = {
  pending: "bg-accent",
  succeeded: "bg-emerald-500",
  failed: "bg-red-500",
  expired: "bg-muted-foreground",
  canceled: "bg-muted-foreground",
}

export function StatusIndicator({ status }: { status: PaymentStatus }) {
  return (
    <span className="inline-flex items-center gap-2 text-sm font-medium">
      <span className={cn("size-2 rounded-full", tone[status], status === "pending" && "animate-pulse")} />
      {status}
    </span>
  )
}
