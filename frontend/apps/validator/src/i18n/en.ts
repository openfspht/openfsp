import type {
  LevelVerdict,
  ScenarioVerdict,
  ValidatorTarget,
} from "@workspace/types"

import type { VerificationCheck } from "@/features/attestation/attestation-model"

export const en = {
  product: "validator",
  nav: { run: "New run" },
  unreachable: "The validator is not reachable.",
  home: {
    title: "Conformance validator",
    description: (concurrency?: number) =>
      `Runs the OpenFSP conformance suite against your implementation. Each run gets its own simulator space, so ${concurrency ?? "several"} people can validate at once without seeing each other's payments. Reports are kept 90 days.`,
    target: "Target",
    targets: {
      gateway: "Gateway",
      "native-operator": "Native operator",
    } satisfies Record<ValidatorTarget, string>,
    targetHints: {
      gateway: "The suite plays the client; the simulator plays the operators.",
      "native-operator": "The suite calls the operator API directly.",
    } satisfies Record<ValidatorTarget, string>,
  },
  prepare: {
    title: "1. Prepare the run",
    description:
      "The validator opens a simulator space for this run only. You then point your gateway at its operator URLs and start.",
    action: "Prepare a run",
    preparing: "Preparing",
    again: "Prepare a new run",
    startBefore: (time: string) =>
      `Start before ${time}. After that, prepare a new run and update the gateway.`,
    expired:
      "This prepared run expired. Prepare a new one and point the gateway at its new URLs.",
  },
  setup: {
    title: "Gateway configuration for this run",
    description:
      "These URLs belong to this run's simulator space: no other run sees its payments, scenarios or journal. Point the gateway under test at them, in the test environment, before you start. Every new run gets new URLs.",
    space: "Simulator space",
    environment: "Environment",
    keyPrefix: "Key prefix",
    derivationKey: "Order id derivation key",
    operator: "Operator",
    baseUrl: "Base URL",
    secondaryUrl: "Secondary URL",
    credentials: "Credentials",
    webhookUrl: "Webhook receiver",
    webhookHint:
      "Register this URL at the gateway as a webhook endpoint for every payment event type before you start: the webhooks.emit scenarios read the events it receives. The last of them answers 410 Gone, after which the gateway may disable the endpoint.",
  },
  form: {
    title: "Run",
    titleAfterPreparation: "2. Start the run",
    needsPreparation: "Prepare the run and configure the gateway first.",
    baseUrl: "Base URL",
    baseUrlPlaceholder: "https://gateway.example.com",
    apiKey: "Test API key",
    apiKeyHint: (prefix: string) =>
      `Starts with ${prefix}. Live keys are refused.`,
    restrictedKey: "Key without statements:read (optional)",
    restrictedKeyHint:
      "A second test key lacking the statements:read scope. Without it, AUTH-6.4 stays assisted and Core cannot pass.",
    derivationKey: "Derivation key",
    derivationKeyHint: "Leave empty to use the key shown above.",
    webhookUrl: "Webhook receiver of this run",
    webhookHint:
      "Registered at the gateway as an endpoint for every payment event type, it lets the webhooks.emit scenarios run.",
    input: (name: string) => `Input ${name}`,
    submit: "Run",
    submitting: "Starting",
    busy: "Every run slot is in use. Try again in a few minutes: your prepared run keeps its simulator space.",
    refused: "The run was refused.",
  },
  run: {
    title: "Run",
    connecting: "Connecting.",
    progress: (done: number, total: number) => `${done} of ${total} scenarios`,
    running: "Running",
    pending: "Pending",
    report: "Open the report",
    lost: "The event stream was interrupted. The report appears once the run ends.",
    failed: "The run stopped",
  },
  report: {
    title: "Report",
    download: "Download JSON",
    notFound: "No report for this run.",
    loading: "Loading the report.",
    date: "Date",
    duration: (ms: number) => `${Math.round(ms / 1000)} s`,
    target: "Target",
    protocol: "Target protocol",
    unknownProtocol: "unknown",
    suite: "Suite",
  },
  attestation: {
    title: "Conformance attestation",
    issue: "Issue the attestation",
    issuing: "Issuing",
    open: "Open the attestation",
    loading: "Loading the attestation.",
    notFound: "No attestation with this id.",
    statement:
      "This attestation records the results of one run of the OpenFSP conformance suite, failures included. It attests results; it does not certify an implementation.",
    coreFailed: "Core did not pass: this implementation is not conformant",
    coreFailedDetail:
      "This attestation records a failure. It is not a conformance claim and must not be presented as one.",
    corePassed: "Core passed",
    corePassedDetail:
      "For this configuration and date only. Cite the version of the implementation tested.",
    verification: "Verification",
    verifying: "Verifying.",
    verified: "Verified",
    notVerified: "Not verified",
    checks: {
      signature: ["Signature valid", "Signature invalid"],
      digest: [
        "Digest matches the stored report",
        "Digest does not match the stored report",
      ],
      key: ["Key published by this validator", "Key unknown"],
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
    protocol: "Announced protocol",
    suite: "Suite",
    report: "Report",
    digest: "Report digest",
    keyId: "Key id",
    configuration: "Configuration",
    downloadPdf: "Download PDF",
    downloadJson: "Download JSON",
    openReport: "Open the report",
    badge: "Badge",
    badgeHint:
      "Shows the Core verdict and the protocol version, and links to this page.",
    markdown: "Markdown",
    html: "HTML",
    validity: "Validity",
    validityStatement:
      "Valid for this configuration and date; stale when the implementation or protocol version changes.",
  },
  profiles: {
    title: "Profiles and their scenarios",
    description:
      "A profile passes when each of its scenarios passes. A profile whose capability the gateway does not announce is not applicable and cannot be claimed.",
  },
  summary: {
    core: "Core",
    profiles: "Profiles",
    noProfiles: "No profile scenario ran.",
    claim: "Claim",
    protocol: (version: string) => `protocol ${version}`,
    noClaim: "No conformance claim: Core did not pass.",
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
    action: "Action to take",
    reason: "Reason",
    details: "Details",
    empty: "No scenario for this target.",
    areas: {
      authentication: "Authentication",
      capabilities: "Capabilities",
      errors: "Errors",
      failures: "Operator failures",
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
