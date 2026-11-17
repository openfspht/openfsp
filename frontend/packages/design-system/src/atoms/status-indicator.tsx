import type { PaymentStatus } from "@workspace/types"
import { cn } from "@workspace/ui/lib/utils"

import { useCopy } from "../copy"

const tone: Record<PaymentStatus, string> = {
    pending: "bg-accent",
    succeeded: "bg-emerald-500",
    failed: "bg-red-500",
    expired: "bg-muted-foreground",
    canceled: "bg-muted-foreground",
}

export function StatusIndicator({ status }: { status: PaymentStatus }) {
    const copy = useCopy()
    return (
        <span className="inline-flex items-center gap-2 text-sm font-medium">
            <span
                className={cn(
                    "size-2 rounded-full",
                    tone[status],
                    status === "pending" && "animate-pulse"
                )}
            />
            {copy.status[status]}
        </span>
    )
}
