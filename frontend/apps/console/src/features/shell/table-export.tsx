import { Button, useCopy } from "@workspace/design-system"
import { FileSpreadsheet, FileText } from "lucide-react"

import { exportToExcel, exportToPdf, type ExportTable } from "@/lib/export-table"

export function TableExport({ table }: { table: ExportTable }) {
    const copy = useCopy().export
    return (
        <div role="group" aria-label={copy.label} className="flex gap-2">
            <Button
                size="sm"
                variant="outline"
                disabled={table.empty}
                onClick={() => void exportToExcel(table)}
            >
                <FileSpreadsheet />
                {copy.excel}
            </Button>
            <Button
                size="sm"
                variant="outline"
                disabled={table.empty}
                onClick={() => void exportToPdf(table)}
            >
                <FileText />
                {copy.pdf}
            </Button>
        </div>
    )
}
