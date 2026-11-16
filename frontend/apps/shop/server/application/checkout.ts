import { ApiError, Money, type PaymentRequest } from "@openfsp/sdk"
import { toE164 } from "@workspace/phone"
import type { Product } from "../domain/catalog"
import { adoptCreatedPayment, syncWithPayment, type Order } from "../domain/order"
import type { Clock, Orders, PaymentGateway } from "./ports"

const MAX_QUANTITY = 20
const MAX_DESCRIPTION = 200
const REFERENCE_LENGTH = 12

export class InvalidOrder extends Error {}

export class UnknownOrder extends Error {}

export interface ShopProvider {
  id: string
  name: string
  logo_url: string | null
}

export interface CheckoutSettings {
  providers?: string[]
  publicUrl: string
}

export class Checkout {
  private readonly catalog: Product[]
  private readonly gateway: PaymentGateway
  private readonly orders: Orders
  private readonly clock: Clock
  private readonly settings: CheckoutSettings

  constructor(
    catalog: Product[],
    gateway: PaymentGateway,
    orders: Orders,
    clock: Clock,
    settings: CheckoutSettings
  ) {
    this.catalog = catalog
    this.gateway = gateway
    this.orders = orders
    this.clock = clock
    this.settings = settings
  }

  products(): Product[] {
    return this.catalog
  }

  async providers(): Promise<ShopProvider[]> {
    const offered = await this.gateway.providers()
    return offered
      .filter(
        (provider) =>
          !this.settings.providers ||
          this.settings.providers.includes(provider.id)
      )
      .map((provider) => ({
        id: provider.id,
        name: provider.displayName,
        logo_url: provider.logoUrl ?? null,
      }))
  }

  async place(
    requested: { product_id: string; quantity: number }[],
    phoneNumber: string,
    provider: string
  ): Promise<Order> {
    const lines = requested.map(({ product_id, quantity }) => {
      const product = this.catalog.find(
        (candidate) => candidate.id === product_id
      )
      if (
        !product ||
        !Number.isInteger(quantity) ||
        quantity < 1 ||
        quantity > MAX_QUANTITY
      )
        throw new InvalidOrder(
          "each line needs a known product and a quantity from 1 to 20"
        )
      return {
        product_id,
        name: product.name.en,
        quantity,
        unit_price: product.price,
      }
    })
    const known = (await this.providers()).some(
      (candidate) => candidate.id === provider
    )
    if (lines.length === 0 || !known)
      throw new InvalidOrder(
        "a cart with at least one line and a known provider are required"
      )
    const amount = {
      amount: lines.reduce(
        (total, line) => total + line.unit_price.amount * line.quantity,
        0
      ),
      currency: lines[0]!.unit_price.currency,
    }
    const reference = this.newReference()
    const request: PaymentRequest = {
      reference,
      amount: Money.centimes(amount.amount),
      provider,
      ...(phoneNumber.trim()
        ? { payerPhoneNumber: toE164(phoneNumber) }
        : {}),
      description: lines
        .map((line) => `${line.quantity} x ${line.name}`)
        .join(", ")
        .slice(0, MAX_DESCRIPTION),
      returnUrl: `${this.settings.publicUrl}/orders/${reference}`,
      metadata: {
        items: lines
          .map((line) => `${line.product_id}:${line.quantity}`)
          .join(","),
      },
    }
    const placed: Order = {
      reference,
      lines,
      amount,
      idempotency_key: crypto.randomUUID(),
      provider,
      status: "pending",
      created_at: this.clock.now().toISOString(),
      events: [],
    }
    this.orders.save(placed)
    const payment = await this.createPayment(placed, request)
    const order = adoptCreatedPayment(
      this.orders.find(reference) ?? placed,
      payment
    )
    this.orders.save(order)
    return order
  }

  async read(reference: string): Promise<Order> {
    const order = this.orders.find(reference)
    if (!order) throw new UnknownOrder(reference)
    const payment = order.payment_id
      ? await this.gateway.getPayment(order.payment_id)
      : await this.gateway.findPayment(reference)
    if (!payment) return order
    const current = syncWithPayment(order, payment)
    this.orders.save(current)
    return current
  }

  private newReference(): string {
    for (;;) {
      const reference = `SHOP-${crypto.randomUUID().replaceAll("-", "").slice(0, REFERENCE_LENGTH).toUpperCase()}`
      if (!this.orders.find(reference)) return reference
    }
  }

  private async createPayment(
    order: Order,
    request: PaymentRequest
  ) {
    try {
      return await this.gateway.createPayment(request, order.idempotency_key)
    } catch (failure) {
      if (failure instanceof ApiError && failure.effect === "none")
        this.orders.remove(order.reference)
      throw failure
    }
  }

  list(): Order[] {
    return this.orders
      .all()
      .sort((left, right) => right.created_at.localeCompare(left.created_at))
  }
}
