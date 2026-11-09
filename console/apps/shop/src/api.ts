import type { OrderSummary } from "@workspace/design-system"
import type { Collection, Money } from "@workspace/types"

export interface Product {
  id: string
  name: string
  description: string
  price: Money
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, { ...init, headers: { "Content-Type": "application/json" } })
  const payload = await response.json()
  if (!response.ok) throw new Error(payload.title ?? payload.error ?? `${path} answered ${response.status}`)
  return payload as T
}

export const shop = {
  products: () => request<Collection<Product>>("/api/products"),
  orders: () => request<Collection<OrderSummary>>("/api/orders"),
  order: (productId: string, phoneNumber: string) =>
    request<OrderSummary>("/api/orders", { method: "POST", body: JSON.stringify({ product_id: productId, phone_number: phoneNumber }) }),
}
