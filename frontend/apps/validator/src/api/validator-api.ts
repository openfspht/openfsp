import type {
  ConformanceReport,
  PrepareRequest,
  RunCreated,
  RunPrepared,
  RunRequest,
  SimulatorConfig,
  SuiteDescription,
  ValidatorProblem,
} from "@workspace/types"

export class ProblemError extends Error {
  readonly problem: ValidatorProblem

  constructor(problem: ValidatorProblem) {
    super(problem.detail ?? problem.title)
    this.problem = problem
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    ...init,
    headers: { Accept: "application/json", ...init?.headers },
  })
  if (!response.ok) {
    const problem = (await response.json().catch(() => undefined)) as
      ValidatorProblem | undefined
    throw new ProblemError(
      problem ?? {
        type: "about:blank",
        title: `${init?.method ?? "GET"} ${path} answered ${response.status}`,
        status: response.status,
      }
    )
  }
  return (await response.json()) as T
}

export const reportUrl = (id: string) =>
  `/api/runs/${encodeURIComponent(id)}/report`

export const eventsUrl = (id: string) =>
  `/api/runs/${encodeURIComponent(id)}/events`

export const validatorApi = {
  suite: () => request<SuiteDescription>("/api/suite"),
  prepare: (body: PrepareRequest) =>
    request<RunPrepared>("/api/runs", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    }),
  simulatorConfig: (id: string) =>
    request<SimulatorConfig>(
      `/api/runs/${encodeURIComponent(id)}/simulator-config`
    ),
  start: (id: string, body: RunRequest) =>
    request<RunCreated>(`/api/runs/${encodeURIComponent(id)}/start`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    }),
  report: (id: string) => request<ConformanceReport>(reportUrl(id)),
}

export const queryKeys = {
  suite: ["validator", "suite"] as const,
  simulatorConfig: (id: string) =>
    ["validator", "simulator-config", id] as const,
  report: (id: string) => ["validator", "report", id] as const,
}
