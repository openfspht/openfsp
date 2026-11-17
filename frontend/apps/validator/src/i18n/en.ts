import type {
  LevelVerdict,
  ScenarioVerdict,
  ValidatorTarget,
} from "@workspace/types"

import type { VerificationCheck } from "@/features/attestation/attestation-model"

export const en = {
  product: "Validator",
  nav: { run: "New run" },
  unreachable:
    "Unable to reach the validator. Check your connection and try again.",
  home: {
    title: "Conformance validator",
    description: (concurrency?: number) =>
      `Assess your implementation using the OpenFSP conformance suite. Each run has an isolated simulator space: ${concurrency ?? "several"} users can run validations concurrently without accessing payments from other runs. Reports are retained for 90 days.`,
    target: "Target",
    targets: {
      server: "Server",
    } satisfies Record<ValidatorTarget, string>,
    targetHints: {
      server:
        "The suite acts as a client and calls the OpenFSP API of the server under test.",
    } satisfies Record<ValidatorTarget, string>,
  },
  prepare: {
    title: "Reference mock server (optional)",
    description:
      "To test mock_openfsp, the reference mock server, prepare a run and use the base URL and key for its space shown below. The suite controls the simulated payment outcomes in that space. Other servers do not require this preparation.",
    action: "Prepare a run",
    preparing: "Preparing",
    again: "Prepare a new run",
    startBefore: (time: string) =>
      `Start the run before ${time}. After this deadline, prepare a new run and use the new URLs.`,
    expired:
      "The preparation for this run has expired. Prepare a new run and use the new URLs.",
  },
  setup: {
    title: "Simulator space for this run",
    description:
      "Use the mock_openfsp base URL and API key to configure the target. This space's payments and scenarios are isolated from other runs. Each new run has its own URLs.",
    space: "Simulator space",
    environment: "Environment",
    keyPrefix: "Key prefix",
    provider: "Payment service",
    baseUrl: "Base URL",
    credentials: "Credentials",
    webhookUrl: "Webhook receiver",
    webhookHint:
      "Before starting the run, register this URL on the server as a webhook endpoint for all payment event types. The webhooks.emit scenarios inspect the events received. During the final scenario, the receiver responds with 410 Gone; the server may then disable the endpoint.",
  },
  form: {
    title: "Run",
    titleAfterPreparation: "2. Start the run",
    baseUrl: "Base URL",
    baseUrlPlaceholder: "https://api.example.com",
    apiKey: "Test API key",
    apiKeyHint: (prefix: string) =>
      `Use a key starting with ${prefix}. Production keys are rejected.`,
    restrictedKey: "Key without statements:read (optional)",
    restrictedKeyHint:
      "Provide a second test key without the statements:read scope. Without this key, scenario AUTH-6.4 remains assisted and Core cannot pass.",
    webhookUrl: "Webhook receiver for this run",
    webhookHint:
      "Register this receiver on the server for all payment event types to run the webhooks.emit scenarios.",
    input: (name: string) => `Input ${name}`,
    submit: "Run",
    submitting: "Starting",
    busy: "All execution slots are in use. Try again in a few minutes. Your prepared run retains its simulator space.",
    refused:
      "The run was rejected. Check the supplied parameters before trying again.",
  },
  run: {
    title: "Run",
    connecting: "Connecting",
    progress: (done: number, total: number) => `${done} of ${total} scenarios`,
    running: "Running",
    pending: "Pending",
    report: "View report",
    lost: "The event stream was interrupted. View the report when it becomes available at the end of the run.",
    failed: "Run interrupted",
  },
  report: {
    title: "Report",
    download: "Download JSON",
    notFound: "No report matches this run. Check the run identifier.",
    loading: "Loading report",
    date: "Date",
    duration: (ms: number) => `${Math.round(ms / 1000)} s`,
    target: "Target",
    protocol: "Target protocol",
    unknownProtocol: "unknown",
    suite: "Conformance suite",
  },
  attestation: {
    title: "Conformance attestation",
    issue: "Issue the attestation",
    issuing: "Issuing",
    open: "View attestation",
    loading: "Loading attestation",
    notFound:
      "No attestation matches this identifier. Check the identifier used.",
    statement:
      "This attestation records the results of a single run of the OpenFSP conformance suite, including failures. It attests to the observed results and does not constitute certification of the implementation.",
    coreFailed:
      "Core failed: implementation not conformant in the tested configuration",
    coreFailedDetail:
      "This attestation records a failure. It does not constitute a conformance claim and must not be presented as one.",
    corePassed: "Core passed",
    corePassedDetail:
      "This result applies only to the tested configuration and the run date. State the version of the implementation tested.",
    verification: "Verification",
    verifying: "Verifying",
    verified: "Verified",
    notVerified: "Not verified",
    checks: {
      signature: ["Signature valid", "Signature invalid"],
      digest: [
        "Digest matches the stored report",
        "Digest does not match the stored report",
      ],
      key: ["Key published by this validator", "Unknown key"],
    } satisfies Record<VerificationCheck, [string, string]>,
    keyStatus: (status: string) =>
      ({ active: "active", retired: "retired" })[status] ?? status,
    offline: "Verify offline with the key published at",
    details: "Details",
    issuer: "Issuer",
    issuedAt: "Issued at",
    id: "Attestation",
    runDate: "Run date",
    target: "Target",
    baseUrl: "Base URL",
    protocol: "Advertised protocol version",
    suite: "Conformance suite",
    report: "Report",
    digest: "Report digest",
    keyId: "Key identifier",
    configuration: "Configuration",
    downloadPdf: "Download PDF",
    downloadJson: "Download JSON",
    openReport: "View report",
    badge: "Badge",
    badgeHint:
      "The badge displays the Core verdict and protocol version and links to this page.",
    markdown: "Markdown",
    html: "HTML",
    validity: "Validity",
    validityStatement:
      "This attestation applies to the tested configuration and run date. It no longer applies if the implementation or protocol version changes.",
  },
  profiles: {
    title: "Profiles and scenarios",
    description:
      "A profile passes when all its scenarios pass. If the server does not advertise the corresponding capability, the profile is not applicable and conformance to it cannot be claimed.",
  },
  summary: {
    core: "Core",
    profiles: "Profiles",
    noProfiles: "No profile scenarios were executed.",
    claim: "Conformance claim",
    protocol: (version: string) => `protocol ${version}`,
    noClaim: "Conformance cannot be claimed: Core did not pass.",
    limits: "Limits of this report",
  },
  results: {
    scenario: "Scenario",
    requirement: "Requirement",
    verdict: "Verdict",
    expected: "Expected",
    observed: "Observed",
    step: "Step",
    exchange: "Exchange",
    action: "Required action",
    reason: "Reason",
    details: "Details",
    empty: "No scenario for this target.",
    areas: {
      authentication: "Authentication",
      capabilities: "Capabilities",
      errors: "Errors",
      failures: "Payment outcomes",
      idempotency: "Idempotency",
      lifecycle: "Lifecycle",
      payments: "Payments",
      statements: "Statements",
      webhooks: "Webhooks",
    } as Record<string, string>,
  },
  verdicts: {
    PASS: "pass",
    FAIL: "fail",
    NOT_APPLICABLE: "not applicable",
    INCONCLUSIVE: "inconclusive",
    ASSISTED: "assisted",
  } satisfies Record<ScenarioVerdict, string>,
  levels: {
    PASS: "pass",
    FAIL: "fail",
    INCOMPLETE: "incomplete",
    NOT_APPLICABLE: "not applicable",
  } satisfies Record<LevelVerdict, string>,
}

export type ValidatorCopy = typeof en
