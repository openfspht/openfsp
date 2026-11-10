# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

- **Demo audience and master's jury.** Watch the author buy from a believable Haitian merchant and pay through MonCash, NatCash or a simulated operator during the MBDS defence.
- **Gateway developer.** Uses the shop to exercise the OpenFSP gateway end to end: create a payment, follow the redirect or the phone approval, receive signed webhooks, read the final status from the gateway.
- **Merchant integrator (secondary).** Reads the shop as the reference integration of the OpenFSP client in a merchant application.

## Product Purpose

The shop is the demo merchant of the OpenFSP gateway. It plays a fictional Haitian fine grocery that sells coffee, cacao, fruit, rum and spices online, with a catalog, product pages, a cart and a checkout. The checkout creates one OpenFSP payment for the cart total with the provider the buyer picks, and the order status comes only from webhooks the shop verified and from the gateway, never from the return link. Success: an audience believes it is watching a real shop, and a developer sees every step of the payment protocol from the buyer's side.

## Positioning

A merchant built on an open payment standard: the buyer picks MonCash, NatCash or another operator at checkout, and the merchant code is the same for all of them. The providers on offer are whatever the gateway announces in `/v1/capabilities`, with their display names and logos.

## Operating Context

- Served by Bun next to the gateway in development (`make dev`, port 5174, API on 3000) and in the Docker demo.
- Payments go through the merchant's own OpenFSP gateway with the demo API key; the return URL is `/orders/<reference>`.
- Test environment only: simulated operators, or MonCash and NatCash sandboxes in `make dev-sandbox`.

## Capabilities and Constraints

- Catalog, product page, cart with quantities, checkout (phone number in E.164, provider), order confirmation and return page, orders list with verified webhook events.
- One payment per order, for the cart total in HTG; amounts are integers in centimes on the wire.
- The provider list and names come from the gateway; `OPENFSP_PROVIDERS` may narrow it.
- Orders are kept in memory; a restart forgets them.
- English source strings with a French translation; Haitian audience.

## Brand Commitments

- The merchant is fictional and must read as a plausible Haitian business, never as a real one. Its name, products and copy are authored for the demo.
- OpenFSP appears only where a real merchant would show a payment partner: at checkout and on the order status.

## Evidence on Hand

- Current catalog: coffee beans from Thiotte, Francique mangoes, dark chocolate (`server/domain/catalog.ts`).
- No real product photography, customers, reviews or prices; any shown must be authored and never presented as real testimonials.

## Product Principles

- The status a buyer sees is the one the gateway and the verified webhooks say, never the URL.
- Paying feels like a real Haitian shop: HTG prices, local products, MonCash and NatCash first.
- Every protocol step stays visible to someone who looks for it, without getting in a buyer's way.
