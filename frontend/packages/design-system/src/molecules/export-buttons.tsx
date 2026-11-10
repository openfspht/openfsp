import { FileSpreadsheet, FileText } from "lucide-react"

import { Button } from "../atoms"
import { useCopy } from "../copy"

interface ExportButtonsProps {
  onExcel: () => void
  onPdf: () => void
  disabled?: boolean
}

export function ExportButtons({ onExcel, onPdf, disabled }: ExportButtonsProps) {
  const copy = useCopy().export
  return (
    <div role="group" aria-label={copy.label} className="flex gap-2">
      <Button size="sm" variant="outline" disabled={disabled} onClick={onExcel}>
        <FileSpreadsheet />
        {copy.excel}
      </Button>
      <Button size="sm" variant="outline" disabled={disabled} onClick={onPdf}>
        <FileText />
        {copy.pdf}
      </Button>
    </div>
  )
}
