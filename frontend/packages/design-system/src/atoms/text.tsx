import type { Money as MoneyValue } from "@workspace/types"
import { cn } from "@workspace/ui/lib/utils"

import { useCopy } from "../copy"
import { formatDateTime, formatMoney, formatTime } from "../lib/format"

type TextProps = React.ComponentProps<"span">

export function Tabular({ className, ...props }: TextProps) {
  return <span className={cn("tabular-nums", className)} {...props} />
}

export function Muted({ className, ...props }: TextProps) {
  return <span className={cn("text-muted-foreground", className)} {...props} />
}

export function Money({ value, className }: { value: MoneyValue; className?: string }) {
  const { locale } = useCopy()
  return <span className={cn("tabular-nums", className)}>{formatMoney(value, locale)}</span>
}

export function Time({ value, withDate = false, className }: { value: string; withDate?: boolean; className?: string }) {
  const { locale } = useCopy()
  return (
    <time dateTime={value} className={cn("tabular-nums", className)}>
      {withDate ? formatDateTime(value, locale) : formatTime(value, locale)}
    </time>
  )
}

export function Heading({ className, ...props }: React.ComponentProps<"h3">) {
  return <h3 className={cn("font-heading text-sm font-medium", className)} {...props} />
}

export function ErrorText({ className, ...props }: React.ComponentProps<"p">) {
  return <p className={cn("text-sm text-red-600", className)} {...props} />
}

export function EmptyText({ className, ...props }: React.ComponentProps<"p">) {
  return <p className={cn("text-sm text-muted-foreground", className)} {...props} />
}
