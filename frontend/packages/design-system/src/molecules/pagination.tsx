import { ChevronLeft, ChevronRight } from "lucide-react"

import { Button, Muted } from "../atoms"
import { useCopy } from "../copy"

interface PaginationProps {
    page: number
    totalPages: number
    totalElements: number
    onChange: (page: number) => void
}

export function Pagination({ page, totalPages, totalElements, onChange }: PaginationProps) {
    const copy = useCopy().pagination
    if (totalElements === 0 || totalPages <= 1) {
        return null
    }
    const pages = Math.max(totalPages, 1)
    return (
        <nav
            aria-label={copy.label}
            className="flex flex-wrap items-center justify-between gap-3 pt-4"
        >
            <Muted className="text-sm">{copy.results(totalElements)}</Muted>
            <div className="flex items-center gap-3">
                <Button
                    size="icon-sm"
                    variant="outline"
                    aria-label={copy.previous}
                    disabled={page <= 0}
                    onClick={() => onChange(page - 1)}
                >
                    <ChevronLeft />
                </Button>
                <span className="text-sm tabular-nums">{copy.pageOf(page + 1, pages)}</span>
                <Button
                    size="icon-sm"
                    variant="outline"
                    aria-label={copy.next}
                    disabled={page + 1 >= pages}
                    onClick={() => onChange(page + 1)}
                >
                    <ChevronRight />
                </Button>
            </div>
        </nav>
    )
}
