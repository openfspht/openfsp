import type {
  CreatePaymentRequest,
  WebhookEvent,
} from "@workspace/openfsp-client"
import type { Payment, ProviderSummary } from "@workspace/types"

import type { Order } from "../domain/order"

export interface PaymentGateway {
  createPayment(request: CreatePaymentRequest): Promise<Payment>
  getPayment(id: string): Promise<Payment>
  providers(): Promise<ProviderSummary[]>
}

export interface Orders {
  save(order: Order): void
  find(reference: string): Order | undefined
  all(): Order[]
}

export interface ProcessedEvents {
  has(eventId: string): boolean
  remember(eventId: string): void
}

export type Authenticity =
  { verified: true; event: WebhookEvent } | { verified: false; reason: string }

export interface WebhookAuthenticity {
  verify(request: {
    method: string
    headers: Headers
    body: Uint8Array<ArrayBuffer>
  }): Promise<Authenticity>
}

export interface Clock {
  now(): Date
}
