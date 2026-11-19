import type {
    ConformanceLimit,
    ConformanceReport,
    LevelVerdict,
    PlannedScenario,
    ProfileResult,
    ResultEntry,
    RunStartedEvent,
    RunSummaryEvent,
    ScenarioFinishedEvent,
    ScenarioStartedEvent,
    ValidatorProblem,
} from "@workspace/types"

export type RowStatus = "pending" | "running" | "done"

export interface ResultRow {
    planned: PlannedScenario
    status: RowStatus
    result?: ResultEntry
}

export interface RunState {
    run?: RunStartedEvent
    rows: Record<number, ResultRow>
    summary?: RunSummaryEvent
    failure?: ValidatorProblem
}

export type RunEvent =
    | { name: "run"; data: RunStartedEvent }
    | { name: "scenario-started"; data: ScenarioStartedEvent }
    | { name: "scenario-finished"; data: ScenarioFinishedEvent }
    | { name: "summary"; data: RunSummaryEvent }
    | { name: "failed"; data: ValidatorProblem }

export const RUN_EVENTS = [
    "run",
    "scenario-started",
    "scenario-finished",
    "summary",
    "failed",
] as const

export const initialRunState: RunState = { rows: {} }

export function isTerminal(state: RunState): boolean {
    return state.summary !== undefined || state.failure !== undefined
}

export function reduceRun(state: RunState, event: RunEvent): RunState {
    switch (event.name) {
        case "run": {
            const rows: Record<number, ResultRow> = {}
            for (const planned of event.data.scenarios) {
                rows[planned.index] = state.rows[planned.index] ?? {
                    planned,
                    status: "pending",
                }
            }
            return { ...state, run: event.data, rows }
        }
        case "scenario-started": {
            const planned = event.data.scenario
            const current = state.rows[planned.index]
            if (current?.status === "done") {
                return state
            }
            return {
                ...state,
                rows: {
                    ...state.rows,
                    [planned.index]: { planned, status: "running" },
                },
            }
        }
        case "scenario-finished": {
            const current = state.rows[event.data.index]
            const planned = current?.planned ?? plannedOf(event.data.index, event.data.result)
            return {
                ...state,
                rows: {
                    ...state.rows,
                    [event.data.index]: {
                        planned,
                        status: "done",
                        result: event.data.result,
                    },
                },
            }
        }
        case "summary":
            return { ...state, summary: event.data }
        case "failed":
            return { ...state, failure: event.data }
    }
}

export function plannedOf(index: number, result: ResultEntry): PlannedScenario {
    return {
        index,
        scenario: result.scenario,
        title: result.title,
        area: result.scenario.split("/")[0] ?? "",
        requirement_id: result.requirement_id,
        requirement: result.requirement,
        level: result.level,
        assisted: result.assisted,
    }
}

export function rowsOfReport(report: ConformanceReport): ResultRow[] {
    return report.results.map((result, position) => ({
        planned: plannedOf(position + 1, result),
        status: "done" as const,
        result,
    }))
}

export interface AreaGroup {
    area: string
    rows: ResultRow[]
}

export function groupByArea(rows: ResultRow[]): AreaGroup[] {
    const groups = new Map<string, ResultRow[]>()
    for (const row of [...rows].sort((a, b) => a.planned.index - b.planned.index)) {
        const group = groups.get(row.planned.area) ?? []
        group.push(row)
        groups.set(row.planned.area, group)
    }
    return [...groups].map(([area, grouped]) => ({ area, rows: grouped }))
}

export const CORE_LEVEL = "core"

export interface ProfileGroup {
    name: string
    verdict?: LevelVerdict
    reason?: string
    rows: ResultRow[]
}

export function groupByProfile(rows: ResultRow[], profiles: ProfileResult[] = []): ProfileGroup[] {
    const groups = new Map<string, ResultRow[]>()
    for (const row of [...rows].sort((a, b) => a.planned.index - b.planned.index)) {
        if (row.planned.level === CORE_LEVEL) {
            continue
        }
        const group = groups.get(row.planned.level) ?? []
        group.push(row)
        groups.set(row.planned.level, group)
    }
    const names = new Set([...groups.keys(), ...profiles.map((p) => p.name)])
    return [...names].sort().map((name) => {
        const profile = profiles.find((candidate) => candidate.name === name)
        return {
            name,
            verdict: profile?.verdict,
            reason: profile?.reason,
            rows: groups.get(name) ?? [],
        }
    })
}

export function doneCount(state: RunState): number {
    return Object.values(state.rows).filter((row) => row.status === "done").length
}

export function specLink(specUrl: string | undefined, requirement: string): string | undefined {
    if (!specUrl) {
        return undefined
    }
    return `${specUrl.endsWith("/") ? specUrl : `${specUrl}/`}${requirement}`
}

export function limitsOf(state: RunState): ConformanceLimit[] {
    return state.summary?.limits ?? state.run?.limits ?? []
}
