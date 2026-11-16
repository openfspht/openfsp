import type { Payment, PaymentEvent, PaymentRequest, Provider } from "@openfsp/sdk"

import type { Order } from "../domain/order"

export interface PaymentGateway {
  createPayment(request: PaymentRequest, idempotencyKey: string): Promise<Payment>
  getPayment(id: string): Promise<Payment>
  findPayment(reference: string): Promise<Payment | undefined>
  providers(): Promise<readonly Pick<Provider, "id" | "displayName" | "logoUrl">[]>
}

export interface Orders {
  save(order: Order): void
  remove(reference: string): void
  find(reference: string): Order | undefined
  all(): Order[]
}

export interface ProcessedEvents {
  has(eventId: string): boolean
  remember(eventId: string): void
}

export type Authenticity =
  { verified: true; event: PaymentEvent } | { verified: false; reason: string }

export interface WebhookAuthenticity {
  verify(request: Request): Promise<Authenticity>
}

export interface Clock {
  now(): Date
}
