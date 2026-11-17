import { TableCell, TableRow } from "@workspace/ui/components/table"

export function TableEmptyRow({
    columns,
    children,
}: {
    columns: number
    children: React.ReactNode
}) {
    return (
        <TableRow>
            <TableCell colSpan={columns} className="py-10 text-center text-muted-foreground">
                {children}
            </TableCell>
        </TableRow>
    )
}
