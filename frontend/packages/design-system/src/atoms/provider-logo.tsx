import { cn } from "@workspace/ui/lib/utils"

const sizes = {
  sm: "h-6 w-[4.2rem]",
  md: "h-10 w-28",
}

export function ProviderLogo({ src, size = "md" }: { src?: string | null; size?: keyof typeof sizes }) {
  return (
    <span className={cn("flex shrink-0 items-center", src && "dark:bg-foreground dark:px-1.5", sizes[size])}>
      {src && <img src={src} alt="" className="max-h-full max-w-full object-contain object-left" />}
    </span>
  )
}
