import { Button } from "@workspace/design-system"
import type { Outcome } from "@workspace/types"

import { useI18n } from "@/i18n/i18n-provider"

export interface Resolution {
    outcome: Outcome
    reason?: string
}

const actions = [
    { key: "succeed", resolution: { outcome: "SUCCESSFUL" }, variant: "default" },
    {
        key: "decline",
        resolution: { outcome: "FAILED", reason: "DECLINED" },
        variant: "destructive",
    },
    { key: "expire", resolution: { outcome: "EXPIRED" }, variant: "outline" },
] as const satisfies readonly {
    key: string
    resolution: Resolution
    variant: "default" | "outline" | "destructive"
}[]

export function ResolutionActions({
    disabled,
    onResolve,
}: {
    disabled?: boolean
    onResolve: (resolution: Resolution) => void
}) {
    const copy = useI18n().t.resolution
    return (
        <div className="flex gap-2">
            {actions.map(({ key, resolution, variant }) => (
                <Button
                    key={key}
                    size="sm"
                    variant={variant}
                    disabled={disabled}
                    onClick={() => onResolve(resolution)}
                >
                    {copy[key]}
                </Button>
            ))}
        </div>
    )
}
