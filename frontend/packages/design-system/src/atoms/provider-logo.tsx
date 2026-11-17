import { cn } from "@workspace/ui/lib/utils"

const sizes = {
    sm: "h-8 w-24 p-1",
    md: "h-12 w-36 p-1.5",
}

export function ProviderLogo({
    src,
    size = "md",
}: {
    src?: string | null
    size?: keyof typeof sizes
}) {
    return (
        <span
            className={cn(
                "flex shrink-0 items-center justify-center",
                src && "bg-background ring-1 ring-border dark:bg-foreground",
                sizes[size]
            )}
        >
            {src && <img src={src} alt="" className="max-h-full max-w-full object-contain" />}
        </span>
    )
}
