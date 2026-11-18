# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

- **Merchant engineer.** Hosts the OpenFSP gateway, configures providers, issues and revokes API keys, rotates the webhook signing key, watches webhook deliveries.
- **Support and operations agent.** Finds a payment from a customer complaint (reference, provider reference, amount), reads its transitions and deliveries, attests a payment by hand when no automatic source settles it.
- **Auditor.** Reads the audit trail and the statements without changing anything.
- **Master's jury and demo audience.** The console is also how the author shows the protocol working during the MBDS defence.

## Product Purpose

The console is the operator interface of the OpenFSP gateway, the self-hosted reference implementation of an open payment standard for Haiti. It shows what the gateway knows (payments, providers and their capabilities, statements, keys, webhooks, audit) and exposes the few operator actions the protocol allows. Success: an operator answers "what happened to this payment, and why" in seconds, and every sensitive action leaves an audit record.

## Positioning

It is the console of a gateway the merchant hosts, not of a payment provider: it never moves money, and it sees several operators (MonCash, NatCash, simulated operators) through one protocol. Statuses and finality follow the OpenFSP specification, not an operator's vocabulary.

## Operating Context

- Runs next to the gateway (Vite dev proxy or nginx in Docker), reaches only `/admin` and the gateway's public endpoints.
- The simulator has its own separate interface; the console never talks to it.
- Test environment today (simulated operators, sandbox credentials); live mode must look unmistakably different.

## Capabilities and Constraints

- Stack: React 19, Vite, TanStack Query, shadcn on Base UI, atomic design system in `frontend/packages/design-system` (atoms, molecules, organisms, templates), shared types in `frontend/packages/types`.
- Access: one admin token, verified by the gateway against an Argon2id hash in `OPENFSP_ADMIN_TOKEN`; a 20 minute `HttpOnly`, `SameSite=Strict` session cookie; sign-out; every admin route requires the session. No roles.
- Admin API: payments (latest, detail with transitions and deliveries, attestation), providers, statements (list and CSV export), API keys (issue, revoke), webhook endpoints and signing key rotation, audit trail.
- Languages: English and French, through translation files; source strings and keys stay English; the operator picks the language.

## Evidence on Hand

No real customers, volumes or testimonials. Data shown comes from the running gateway and its simulated operators; never fabricate figures.

## Product Principles

1. The payment's story first: status, provider, amount, then every transition and delivery that led there.
2. Read freely, act deliberately: sensitive actions are explicit and audited.
3. The protocol's words, not an operator's: OpenFSP statuses, capabilities and problem types.
4. Test and live are never confused.
