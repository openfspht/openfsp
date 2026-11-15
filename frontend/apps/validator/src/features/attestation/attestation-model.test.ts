import { describe, expect, test } from "bun:test"
import type { AttestationResource } from "@workspace/types"

import {
  badgeHtml,
  badgeMarkdown,
  checksOf,
  coreVerdictText,
} from "./attestation-model"

const id = "7d0c2a4e-1f3b-4c6d-8e9f-0a1b2c3d4e5f"

const resource = (
  core: "PASS" | "FAIL" | "INCOMPLETE"
): AttestationResource => ({
  id,
  attestation: {
    type: "openfsp-conformance-attestation/1",
    id,
    issuer: "https://validator.example",
    issued_at: "2026-11-11T12:00:00Z",
    statement: "This attestation records the results of one run.",
    claim: {
      target: "gateway",
      level: core === "PASS" ? "core" : "none",
      profiles: [],
      protocol_version: "0.1.0",
    },
    suite: { version: "0.1.0", protocol_version: "0.1.0" },
    target: { kind: "gateway", base_url: "https://gateway.example" },
    run: { id: "run-1", date: "2026-11-11T11:58:00Z", duration_ms: 1000 },
    configuration: { api_key: "[REDACTED]" },
    verdicts: { core, profiles: [] },
    report: {
      id: "run-1",
      digest_algorithm: "sha-256",
      canonicalization: "RFC 8785",
      digest: "ab".repeat(32),
    },
    limits: [],
    validity: {
      statement: "Valid for this configuration and date.",
      reference: "conformance#6.4",
    },
  },
  signature: {
    format: "jws-compact",
    alg: "EdDSA",
    kid: "kid-1",
    jws: "a.b.c",
  },
  links: {
    page: `https://validator.example/attestations/${id}`,
    verify: `/api/attestations/${id}/verify`,
    pdf: `/api/attestations/${id}.pdf`,
    badge: `/api/attestations/${id}/badge.svg`,
    report: "/api/runs/run-1/report",
    keys: "/.well-known/openfsp-validator",
  },
})

describe("attestation badge", () => {
  test("links the absolute badge to the public page and says pass only when core passed", () => {
    expect(badgeMarkdown(resource("PASS"))).toBe(
      `[![OpenFSP core: pass, protocol 0.1.0](https://validator.example/api/attestations/${id}/badge.svg)](https://validator.example/attestations/${id})`
    )
    expect(coreVerdictText(resource("FAIL"))).toBe("fail")
    expect(coreVerdictText(resource("INCOMPLETE"))).toBe("fail")
  })

  test("writes an HTML snippet with escaped attributes", () => {
    expect(badgeHtml(resource("FAIL"))).toBe(
      `<a href="https://validator.example/attestations/${id}"><img src="https://validator.example/api/attestations/${id}/badge.svg" alt="OpenFSP core: fail, protocol 0.1.0"></a>`
    )
  })
})

describe("attestation verification", () => {
  test("reports each check on its own", () => {
    expect(
      checksOf({
        valid: false,
        signature_valid: true,
        key_known: true,
        digest_matches: false,
        problems: ["the report digest does not match the stored report"],
      })
    ).toEqual([
      { check: "signature", ok: true },
      { check: "digest", ok: false },
      { check: "key", ok: true },
    ])
  })
})
