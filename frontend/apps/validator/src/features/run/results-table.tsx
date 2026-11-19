import {
    Card,
    CardContent,
    CardHeader,
    CardTitle,
    ExchangeDetail,
    Heading,
    Muted,
    TableEmptyRow,
    Tabular,
} from "@workspace/design-system"
import type { RedactedExchange, ResultEntry } from "@workspace/types"
import {
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeader,
    TableRow,
} from "@workspace/ui/components/table"
import { ChevronDown, ChevronRight } from "lucide-react"
import { Fragment, useState } from "react"

import { useI18n } from "@/i18n/i18n-provider"

import { groupByArea, specLink, type ResultRow } from "./run-state"
import { VerdictBadge } from "./verdict-badge"

const COLUMNS = 4

export function ResultsTable({ rows, specUrl }: { rows: ResultRow[]; specUrl?: string }) {
    const { t } = useI18n()
    const groups = groupByArea(rows)
    if (groups.length === 0) {
        return (
            <Card>
                <CardContent>
                    <Table>
                        <TableBody>
                            <TableEmptyRow columns={COLUMNS}>{t.results.empty}</TableEmptyRow>
                        </TableBody>
                    </Table>
                </CardContent>
            </Card>
        )
    }
    return (
        <div className="space-y-6">
            {groups.map((group) => (
                <Card key={group.area}>
                    <CardHeader>
                        <CardTitle className="font-heading">
                            {t.results.areas[group.area] ?? group.area}
                        </CardTitle>
                    </CardHeader>
                    <CardContent>
                        <Table>
                            <TableHeader>
                                <TableRow>
                                    <TableHead className="w-8" />
                                    <TableHead>{t.results.scenario}</TableHead>
                                    <TableHead>{t.results.requirement}</TableHead>
                                    <TableHead className="text-right">
                                        {t.results.verdict}
                                    </TableHead>
                                </TableRow>
                            </TableHeader>
                            <TableBody>
                                {group.rows.map((row) => (
                                    <ResultLine
                                        key={row.planned.index}
                                        row={row}
                                        specUrl={specUrl}
                                    />
                                ))}
                            </TableBody>
                        </Table>
                    </CardContent>
                </Card>
            ))}
        </div>
    )
}

function ResultLine({ row, specUrl }: { row: ResultRow; specUrl?: string }) {
    const { t } = useI18n()
    const [open, setOpen] = useState(false)
    const expandable = hasDetail(row.result)
    const link = specLink(specUrl, row.planned.requirement)
    const Chevron = open ? ChevronDown : ChevronRight
    return (
        <Fragment>
            <TableRow
                className={expandable ? "cursor-pointer" : undefined}
                onClick={expandable ? () => setOpen(!open) : undefined}
            >
                <TableCell className="w-8 text-muted-foreground">
                    {expandable && (
                        <button
                            type="button"
                            aria-expanded={open}
                            aria-label={t.results.details}
                            className="flex items-center hover:text-foreground"
                            onClick={(event) => {
                                event.stopPropagation()
                                setOpen(!open)
                            }}
                        >
                            <Chevron className="size-4" />
                        </button>
                    )}
                </TableCell>
                <TableCell className="whitespace-normal">
                    <span className="block">{row.planned.title}</span>
                    <Muted className="text-xs">
                        <Tabular>{row.planned.scenario}</Tabular>
                    </Muted>
                </TableCell>
                <TableCell>
                    <span className="block font-medium">
                        <Tabular>{row.planned.requirement_id}</Tabular>
                    </span>
                    {link ? (
                        <a
                            href={link}
                            target="_blank"
                            rel="noreferrer"
                            className="text-xs text-muted-foreground underline-offset-4 hover:text-foreground hover:underline"
                            onClick={(event) => event.stopPropagation()}
                        >
                            {row.planned.requirement}
                        </a>
                    ) : (
                        <Muted className="text-xs">{row.planned.requirement}</Muted>
                    )}
                </TableCell>
                <TableCell className="text-right">
                    <VerdictBadge verdict={row.result?.verdict} status={row.status} />
                </TableCell>
            </TableRow>
            {open && row.result && (
                <TableRow className="hover:bg-transparent">
                    <TableCell colSpan={COLUMNS} className="bg-muted/30 whitespace-normal">
                        <ResultDetail result={row.result} />
                    </TableCell>
                </TableRow>
            )}
        </Fragment>
    )
}

function hasDetail(result?: ResultEntry): boolean {
    return Boolean(result && (result.failure || result.reason || result.action))
}

function ResultDetail({ result }: { result: ResultEntry }) {
    const { t } = useI18n()
    return (
        <div className="space-y-6 py-4">
            {result.action && (
                <section className="space-y-1">
                    <Heading>{t.results.action}</Heading>
                    <p className="text-sm">{result.action}</p>
                </section>
            )}
            {result.reason && (
                <section className="space-y-1">
                    <Heading>{t.results.reason}</Heading>
                    <p className="text-sm">{result.reason}</p>
                    {result.reason_code && (
                        <Muted className="text-xs">
                            <Tabular>{result.reason_code}</Tabular>
                        </Muted>
                    )}
                </section>
            )}
            {result.failure && (
                <section className="space-y-3">
                    <Heading>
                        {t.results.step}: {result.failure.step}
                    </Heading>
                    <Table>
                        <TableHeader>
                            <TableRow>
                                <TableHead />
                                <TableHead>{t.results.expected}</TableHead>
                                <TableHead>{t.results.observed}</TableHead>
                            </TableRow>
                        </TableHeader>
                        <TableBody>
                            {result.failure.mismatches.map((mismatch, position) => (
                                <TableRow key={position}>
                                    <TableCell className="align-top whitespace-normal text-muted-foreground">
                                        {mismatch.expectation}
                                    </TableCell>
                                    <TableCell className="align-top break-all whitespace-normal">
                                        <Tabular>{mismatch.expected}</Tabular>
                                    </TableCell>
                                    <TableCell className="align-top break-all whitespace-normal">
                                        <Tabular>{mismatch.observed}</Tabular>
                                    </TableCell>
                                </TableRow>
                            ))}
                        </TableBody>
                    </Table>
                </section>
            )}
            {result.failure?.exchange && (
                <section className="space-y-3">
                    <Heading>
                        {t.results.exchange}:{" "}
                        <Tabular className="normal-case">
                            {result.failure.exchange.request.method}{" "}
                            {result.failure.exchange.request.url}
                        </Tabular>
                    </Heading>
                    <ExchangeDetail exchange={toView(result.failure.exchange)} />
                </section>
            )}
        </div>
    )
}

function toView(exchange: RedactedExchange) {
    return {
        status: exchange.response.status,
        requestHeaders: exchange.request.headers,
        requestBody: exchange.request.body ?? "",
        responseHeaders: exchange.response.headers,
        responseBody: exchange.response.body,
    }
}
