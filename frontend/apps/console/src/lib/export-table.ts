export interface ExportTable {
  title: string
  filename: string
  columns: string[]
  rows: () => (string | number)[][]
  empty: boolean
}

const SHEET_NAME_LIMIT = 31
const BRAND: [number, number, number] = [11, 59, 54]
const MARGIN = 14

const NON_BREAKING_SPACES = /[\u00A0\u202F\u2007]/g

const printable = (value: string | number) =>
  String(value).replace(NON_BREAKING_SPACES, " ")

const stamped = (filename: string) =>
  `${filename}-${new Date().toISOString().slice(0, 10)}`

export async function exportToExcel(table: ExportTable) {
  const { default: writeXlsxFile } = await import("write-excel-file/browser")
  const header = table.columns.map((value) => ({
    value,
    fontWeight: "bold" as const,
  }))
  const body = table.rows().map((row) => row.map((value) => ({ value })))
  await writeXlsxFile([header, ...body], {
    sheet: table.title.slice(0, SHEET_NAME_LIMIT),
  }).toFile(`${stamped(table.filename)}.xlsx`)
}

export async function exportToPdf(table: ExportTable) {
  const [{ jsPDF }, { autoTable }] = await Promise.all([
    import("jspdf"),
    import("jspdf-autotable"),
  ])
  const document = new jsPDF({ orientation: "landscape" })
  document.setFontSize(14)
  document.text(printable(table.title), MARGIN, 16)
  autoTable(document, {
    head: [table.columns.map(printable)],
    body: table.rows().map((row) => row.map(printable)),
    startY: 22,
    margin: { left: MARGIN, right: MARGIN },
    styles: { fontSize: 9 },
    headStyles: { fillColor: BRAND },
  })
  document.save(`${stamped(table.filename)}.pdf`)
}
