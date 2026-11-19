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

import { HttpError, post, request } from "@workspace/http"

export type ValidatorRunRequest = RunRequest & { control_endpoint?: string }

export type ValidatorSuite = SuiteDescription & { retention_days?: number }

export const problemOf = (error: unknown): Partial<ValidatorProblem> | undefined =>
    error instanceof HttpError
        ? ((error.problem as Partial<ValidatorProblem> | undefined) ?? { status: error.status })
        : undefined

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
    suite: () => request<ValidatorSuite>("/api/suite"),
    prepare: (body: PrepareRequest) => request<RunPrepared>("/api/runs", post(body)),
    start: (id: string, body: ValidatorRunRequest) =>
        request<RunCreated>(`/api/runs/${encodeURIComponent(id)}/start`, post(body)),
    report: (id: string) => request<ConformanceReport>(reportUrl(id)),
    runAttestation: (runId: string) =>
        request<AttestationResource>(`/api/runs/${encodeURIComponent(runId)}/attestation`),
    issueAttestation: (runId: string) =>
        request<AttestationResource>(`/api/runs/${encodeURIComponent(runId)}/attestation`, post()),
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
