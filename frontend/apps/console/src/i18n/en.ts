import type { DeliveryStatus, TransitionSource } from "@workspace/types"

export const en = {
  nav: {
    label: "Sections",
    activity: "Activity",
    payments: "Payments",
    providers: "Payment services",
    statements: "Statements",
    keys: "API keys",
    webhooks: "Webhooks",
    audit: "Audit",
    configuration: "Configuration",
  },
  activity: {
    title: "Activity",
    description: (date: string) =>
      `Operations recorded by the gateway for business day ${date}, from most recent to oldest.`,
    filter: "Filter by source",
    paymentsToday: "payments today",
    pending: "pending",
    retrying: "deliveries awaiting retry",
    disabled: "webhooks disabled",
    empty: "No events match this filter.",
    unreachable:
      "Unable to reach the gateway. Check your connection and try again.",
    selectHint: "Select an event to view the associated payment history.",
    chips: {
      all: "All",
      creations: "Creations",
      callbacks: "Callbacks",
      lookups: "Lookups",
      statements: "Statements",
      attestations: "Attestations",
      deliveries: "Deliveries",
      access: "Access",
    },
    tags: {
      conflict: "conflict",
      delivery: "delivery",
      statement: "statement",
      access: "access",
    },
    moved: (
      reference: string,
      provider: string,
      from: string | undefined,
      to: string
    ) =>
      from
        ? `${reference} on ${provider}: ${from} → ${to}`
        : `${reference} on ${provider}: ${to}`,
    conflict: (reference: string, reported: string) =>
      `${reference}: the payment service reported ${reported}; this outcome was set aside`,
    delivered: (reference: string, event: string, endpoint: string) =>
      `${reference} ${event} → ${endpoint}`,
    statement: (provider: string, day: string, complete: boolean) =>
      `${provider} statement for ${day}${complete ? ", complete" : ", still open"}`,
    keyIssued: (prefix: string) => `API key ${prefix} issued`,
    keyRevoked: (prefix: string) => `API key ${prefix} revoked`,
    signedIn: "Administrator signed in",
    signInFailed: "Administrator sign-in rejected",
    webhookAdded: (name: string) => `Webhook ${name} added`,
    webhookRemoved: (name: string) => `Webhook ${name} removed`,
    openPayment: "Open payment",
    exportColumns: {
      at: "Date and time",
      source: "Source",
      event: "Event",
      detail: "Detail",
    },
    attempt: (attempt: number, outcome: string, ms: number) =>
      `attempt ${attempt}: ${outcome} in ${ms} ms`,
    search: "Reference, service reference or amount",
    searchLabel: "Search payments",
    noMatch: "No recent payments match your search.",
  },
  payments: {
    title: "Payments",
    description: "Most recent payments recorded by the gateway.",
  },
  payment: {
    back: "All payments",
    notFound: "No payment matches this identifier. Check the identifier used.",
  },
  providers: {
    title: "Payment services",
    description: "Payment services configured on this gateway.",
    empty: "No payment services are configured.",
  },
  statements: {
    title: "Statements",
    description:
      "Statements of payments recorded as settled, grouped by payment service.",
  },
  keys: {
    title: "API keys",
    description:
      "Keys used by merchant applications to access the gateway API.",
    issueDescription:
      "Least privilege applies by default. The secret is displayed only once, when the key is issued.",
    principal: "Principal",
    principalPlaceholder: "merchant app",
    issue: "Issue a key",
    copyNow: "Copy this key now. It will not be displayed again.",
    empty: "No keys have been issued.",
    revoked: "revoked",
    revoke: "Revoke",
    noScope: "no scope",
    lastUsed: "Last used",
    never: "never",
    key: "Key",
    scopes: "Scopes",
    created: "Created",
    status: "Status",
    actions: "Actions",
    active: "active",
    expired: "expired",
    confirmRevoke: "Revoke the key",
    revokeTitle: "Revoke this key?",
    revokeDescription: (principal: string) =>
      `${principal} will no longer be able to use this key to access the gateway. Revocation is irreversible.`,
    cancel: "Cancel",
    issuedTitle: "New API key",
    dismiss: "Done",
  },
  webhooks: {
    title: "Webhooks",
    description:
      "Delivery of signed events to the merchant's webhook endpoints.",
    empty: "No webhook is configured.",
    events: "Events",
    pending: (count: number) => `${count} pending`,
    disabled: "disabled",
    enabled: "enabled",
    off: "disabled in configuration",
    enable: "Re-enable",
    configured: "configuration",
    addTitle: "Add a webhook",
    name: "Name",
    namePlaceholder: "erp",
    nameHint: "Lowercase letters, digits and hyphens.",
    url: "URL",
    urlPlaceholder: "https://merchant.example/webhooks/openfsp",
    add: "Add the webhook",
    remove: "Remove",
    confirmRemove: "Remove the webhook",
    removeTitle: "Remove this webhook?",
    removeDescription: (name: string) =>
      `Endpoint ${name} will no longer receive events. Pending deliveries will not be sent.`,
    rotateTitleConfirm: "Rotate the signing key?",
    rotateConfirmDescription:
      "The new key will be published immediately and used to sign events from tomorrow.",
    status: "Status",
    actions: "Actions",
    cancel: "Cancel",
    rotateTitle: "Signing key",
    rotateDescription:
      "Ed25519 key generated by the gateway. Public keys are available in /.well-known/openfsp-configuration.",
    rotate: "Rotate key",
    rotation: "Automatic rotation",
    every: (minutes: number) =>
      minutes % 1440 === 0
        ? `every ${minutes / 1440} days`
        : minutes % 60 === 0
          ? `every ${minutes / 60} hours`
          : `every ${minutes} minutes`,
    rotationOff: "off",
    nextRotation: "Next rotation",
    keyId: "Key identifier",
    signsFrom: "Signs from",
    phases: { signing: "in use", next: "next key", retired: "retired" },
  },
  audit: {
    title: "Audit",
    description: "Record of operations, associated identities and results.",
    empty: "No operations have been recorded.",
    when: "Date and time",
    kind: "Kind",
    operation: "Operation",
    principal: "Principal",
    result: "Result",
    kinds: {
      request: "request",
      authentication_failure: "authentication failure",
      key_issued: "key issued",
      key_revoked: "key revoked",
      admin_sign_in: "administrator sign-in",
      admin_sign_in_failure: "administrator sign-in rejected",
      webhook_added: "webhook added",
      webhook_removed: "webhook removed",
    } as Record<string, string>,
  },
  configuration: {
    title: "Configuration",
    description:
      "Information published by the gateway and available to clients.",
    discovery: "Discovery document",
    discoveryPath: "Publicly available at /.well-known/openfsp-configuration.",
    capabilities: "Capabilities",
    capabilitiesPath: "Available with an API key at /v1/capabilities.",
  },
  source: {
    creation: "creation",
    lookup: "lookup",
    signed_callback: "signed callback",
    per_payment_url: "payment URL",
    statement: "statement",
    attestation: "attestation",
  } satisfies Record<TransitionSource, string>,
  delivery: {
    pending: "pending",
    succeeded: "delivered",
    abandoned: "abandoned",
    stopped: "stopped",
    nextAttempt: "next attempt",
  } satisfies Record<DeliveryStatus, string> & { nextAttempt: string },
  attestation: {
    confirmTitle: (status: string) =>
      `Attest to status ${status} for this payment?`,
    confirmDescription:
      "The payment enters a terminal state and the corresponding webhook events are sent. This action is irreversible.",
    succeeded: "Attest success",
    failed: "Attest failure",
    expired: "Attest expiry",
    person: "Attestation author",
    personHint: "Name of the person recorded as the attestation author.",
    evidence: "Evidence reference",
    evidenceHint:
      "Reference used to verify the outcome: provider ticket, statement entry or call log.",
  },
  paymentsTable: {
    reference: "Reference",
    provider: "Payment service",
    amount: "Amount",
    status: "Status",
    created: "Created at",
    empty:
      "No payments have been recorded. Create a payment from the demo shop or through the API.",
  },
  paymentDetail: {
    reference: "Reference",
    nextAction: "Next action",
    failureReason: "Failure reason",
    created: "Created at",
    completed: "Completed at",
    via: "via",
    indeterminate: (provider: string) =>
      `The outcome of the creation request to ${provider} is indeterminate. The request was not resent. Verify the outcome with the payment service provider, then record an attestation supported by evidence.`,
    attestationTitle: "Payment outcome attestation",
    attestationAutomatic:
      "This payment service reports outcomes automatically. Record an attestation only when supported by evidence, such as a statement entry.",
    attestationManual:
      "This payment service does not provide an automatic source of outcomes. An attestation supported by evidence is required.",
    lifecycle: "Lifecycle",
    conflict: (source: string, status: string, amount: number) =>
      `Conflict: ${source} reported ${status} for ${amount} cents`,
    webhooks: "Webhooks",
    noEvent: "No events have been recorded.",
    sequence: "sequence",
    noEndpoint: "No webhook endpoints are subscribed.",
  },
  gatewayStatement: {
    today: "today",
    empty: "No payments are recorded as settled.",
    count: (count: number) => (count === 1 ? "1 payment" : `${count} payments`),
    reference: "Reference",
    amount: "Amount",
    settledAt: "Settled at",
  },
  signIn: {
    title: "Gateway console",
    description: "Enter the administration token for this gateway.",
    token: "Administration token",
    rejected:
      "Incorrect administration token. Check the token entered and try again.",
    failed: "Unable to reach the gateway. Check your connection and try again.",
    submit: "Sign in",
  },
}

export type Messages = typeof en
