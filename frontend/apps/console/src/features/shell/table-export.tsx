import { ExportButtons } from "@workspace/design-system"

import { exportToExcel, exportToPdf, type ExportTable } from "@/lib/export-table"

export function TableExport({ table }: { table: ExportTable }) {
    return (
        <ExportButtons
            disabled={table.empty}
            onExcel={() => void exportToExcel(table)}
            onPdf={() => void exportToPdf(table)}
        />
    )
}
