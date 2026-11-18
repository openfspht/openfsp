import type {
    AttestationResource,
    AttestationVerification,
    ConformanceReport,
    PrepareRequest,
    RunCreated,
    RunPrepared,
    RunRequest,
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

export const reportUrl = (id: string) => `/api/runs/${encodeURIComponent(id)}/report`

export const eventsUrl = (id: string) => `/api/runs/${encodeURIComponent(id)}/events`

export const attestationUrls = (id: string) => {
    const base = `/api/attestations/${encodeURIComponent(id)}`
    return {
        pdf: (locale: string) => `${base}.pdf?lang=${encodeURIComponent(locale)}`,
        badge: `${base}/badge.svg`,
    }
}

export const validatorApi = {
    suite: () => request<SuiteDescription>("/api/suite"),
    prepare: (body: PrepareRequest) =>
        request<RunPrepared>("/api/runs", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(body),
        }),
    start: (id: string, body: RunRequest) =>
        request<RunCreated>(`/api/runs/${encodeURIComponent(id)}/start`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(body),
        }),
    report: (id: string) => request<ConformanceReport>(reportUrl(id)),
    runAttestation: (runId: string) =>
        request<AttestationResource>(`/api/runs/${encodeURIComponent(runId)}/attestation`),
    issueAttestation: (runId: string) =>
        request<AttestationResource>(`/api/runs/${encodeURIComponent(runId)}/attestation`, {
            method: "POST",
        }),
    attestation: (id: string) =>
        request<AttestationResource>(`/api/attestations/${encodeURIComponent(id)}`),
    verifyAttestation: (id: string) =>
        request<AttestationVerification>(`/api/attestations/${encodeURIComponent(id)}/verify`),
}

export const queryKeys = {
    suite: ["validator", "suite"] as const,
    report: (id: string) => ["validator", "report", id] as const,
    runAttestation: (runId: string) => ["validator", "run-attestation", runId] as const,
    attestation: (id: string) => ["validator", "attestation", id] as const,
    verification: (id: string) => ["validator", "attestation-verification", id] as const,
}
