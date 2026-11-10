import type {
  EventType,
  Money,
  NextAction,
  Page,
  PaymentStatus,
} from "@workspace/types"

export type Text = { en: string; fr: string }

export type Aisle = "coffee" | "cacao" | "fruit" | "spirits" | "pantry"

export interface Product {
  id: string
  aisle: Aisle
  name: Text
  description: Text
  origin: Text
  unit: Text
  price: Money
}

export interface ShopProvider {
  id: string
  name: string
  logo_url: string | null
}

export interface OrderLine {
  product_id: string
  name: string
  quantity: number
  unit_price: Money
}

export interface Order {
  reference: string
  lines: OrderLine[]
  amount: Money
  payment_id: string
  provider: string
  status: PaymentStatus
  next_action?: NextAction
  created_at: string
  events: {
    id: string
    type: EventType
    sequence: number
    received_at: string
  }[]
}

export class ShopError extends Error {
  readonly status: number

  constructor(message: string, status: number) {
    super(message)
    this.status = status
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    ...init,
    headers: { "Content-Type": "application/json" },
  })
  const payload = await response.json().catch(() => ({}))
  if (!response.ok)
    throw new ShopError(
      payload.detail ??
        payload.title ??
        payload.error ??
        `${path} answered ${response.status}`,
      response.status
    )
  return payload as T
}

export const shop = {
  catalog: () =>
    request<{ data: Product[]; providers: ShopProvider[] }>("/api/products"),
  orders: (page: number) =>
    request<Page<Order>>(`/api/orders?page=${page}&size=12`),
  read: (reference: string) =>
    request<Order>(`/api/orders/${encodeURIComponent(reference)}`),
  order: (
    lines: { product_id: string; quantity: number }[],
    phoneNumber: string,
    provider: string
  ) =>
    request<Order>("/api/orders", {
      method: "POST",
      body: JSON.stringify({ lines, phone_number: phoneNumber, provider }),
    }),
}
