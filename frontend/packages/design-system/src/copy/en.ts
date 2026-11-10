import type { PaymentStatus } from "@workspace/types"

export const en = {
  locale: "en-GB",
  status: {
    pending: "pending",
    succeeded: "succeeded",
    failed: "failed",
    expired: "expired",
    canceled: "canceled",
  } satisfies Record<PaymentStatus, string>,
  clipboard: { copy: "Copy", copied: "Copied" },
  modal: { cancel: "Cancel" },
  pagination: {
    label: "Pages",
    previous: "Previous page",
    next: "Next page",
    pageOf: (page: number, total: number) => `Page ${page} of ${total}`,
    results: (count: number) => (count === 1 ? "1 result" : `${count} results`),
  },
  filters: { all: "All", search: "Search", status: "Status", provider: "Provider", kind: "Kind" },
  export: { label: "Export", excel: "Excel", pdf: "PDF" },
  exchange: {
    status: "Status",
    received: "Received",
    from: "From",
    duration: (ms: number) => `${ms} ms`,
    request: "Request",
    response: "Response",
    noBody: "No body.",
  },
  language: { label: "Language", en: "EN", fr: "FR", switchTo: { en: "Switch to English", fr: "Passer en français" } },
  header: {
    environment: (environment: string) => `${environment} environment`,
    signOut: "Sign out",
    dark: "Dark mode",
    light: "Light mode",
  },
}

export type DesignSystemCopy = typeof en
