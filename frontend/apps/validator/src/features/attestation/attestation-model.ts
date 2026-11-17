import type { AttestationResource, AttestationVerification } from "@workspace/types"

export type VerificationCheck = "signature" | "digest" | "key"

export interface CheckOutcome {
    check: VerificationCheck
    ok: boolean
}

export const coreVerdictText = (resource: AttestationResource) =>
    resource.attestation.verdicts.core === "PASS" ? "pass" : "fail"

export const badgeUrlOf = (resource: AttestationResource) =>
    new URL(resource.links.badge, resource.links.page).toString()

export function badgeAlt(resource: AttestationResource) {
    return `OpenFSP core: ${coreVerdictText(resource)}, protocol ${resource.attestation.claim.protocol_version}`
}

export function badgeMarkdown(resource: AttestationResource) {
    return `[![${badgeAlt(resource)}](${badgeUrlOf(resource)})](${resource.links.page})`
}

const HTML_ENTITIES: Record<string, string> = {
    "&": "&amp;",
    '"': "&quot;",
    "<": "&lt;",
    ">": "&gt;",
}

const escapeHtml = (value: string) =>
    value.replace(/[&"<>]/g, (character) => HTML_ENTITIES[character])

export function badgeHtml(resource: AttestationResource) {
    return `<a href="${escapeHtml(resource.links.page)}"><img src="${escapeHtml(badgeUrlOf(resource))}" alt="${escapeHtml(badgeAlt(resource))}"></a>`
}

export function checksOf(verification: AttestationVerification): CheckOutcome[] {
    return [
        { check: "signature", ok: verification.signature_valid },
        { check: "digest", ok: verification.digest_matches },
        { check: "key", ok: verification.key_known },
    ]
}
