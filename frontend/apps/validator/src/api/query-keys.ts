export const queryKeys = {
    suite: ["validator", "suite"] as const,
    report: (id: string) => ["validator", "report", id] as const,
    runAttestation: (runId: string) => ["validator", "run-attestation", runId] as const,
    attestation: (id: string) => ["validator", "attestation", id] as const,
    verification: (id: string) => ["validator", "attestation-verification", id] as const,
}
