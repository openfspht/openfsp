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

export const problemOf = (error: unknown): Partial<ValidatorProblem> | undefined =>
    error instanceof HttpError
        ? ((error.problem as Partial<ValidatorProblem> | undefined) ?? { status: error.status })
        : undefined

export const reportUrl = (id: string) => `/api/runs/${encodeURIComponent(id)}/report`

export const eventsUrl = (id: string) => `/api/runs/${encodeURIComponent(id)}/events`

export const validatorApi = {
    suite: () => request<SuiteDescription>("/api/suite"),
    prepare: (body: PrepareRequest) => request<RunPrepared>("/api/runs", post(body)),
    start: (id: string, body: RunRequest) =>
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
