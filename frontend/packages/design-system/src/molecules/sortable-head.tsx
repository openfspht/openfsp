import { TableHead } from "@workspace/ui/components/table"
import { cn } from "@workspace/ui/lib/utils"
import { ArrowDown, ArrowUp, ChevronsUpDown } from "lucide-react"

export interface SortState {
  key: string
  ascending: boolean
}

interface SortableHeadProps {
  label: string
  sortKey: string
  sort?: SortState
  onSort?: (key: string) => void
  align?: "left" | "right"
}

export function SortableHead({ label, sortKey, sort, onSort, align = "left" }: SortableHeadProps) {
  const active = sort?.key === sortKey
  if (!onSort) return <TableHead className={cn(align === "right" && "text-right")}>{label}</TableHead>
  const Icon = !active ? ChevronsUpDown : sort.ascending ? ArrowUp : ArrowDown
  return (
    <TableHead aria-sort={active ? (sort.ascending ? "ascending" : "descending") : "none"} className={cn(align === "right" && "text-right")}>
      <button
        type="button"
        onClick={() => onSort(sortKey)}
        className={cn("inline-flex items-center gap-1.5 uppercase outline-none hover:text-foreground focus-visible:underline", align === "right" && "flex-row-reverse", !active && "text-muted-foreground")}
      >
        {label}
        <Icon className="size-3.5" aria-hidden />
      </button>
    </TableHead>
  )
}
