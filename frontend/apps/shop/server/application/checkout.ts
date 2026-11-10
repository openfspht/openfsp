import type { Product } from "../domain/catalog"
import type { Order } from "../domain/order"
import type { Clock, Orders, PaymentGateway } from "./ports"

const MAX_QUANTITY = 20
const MAX_DESCRIPTION = 200

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
        name: provider.display_name,
        logo_url: provider.logo_url,
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
    const reference = `SHOP-${this.clock.now().getTime().toString(36).toUpperCase()}`
    const payment = await this.gateway.createPayment({
      reference,
      amount,
      provider,
      ...(phoneNumber ? { payer: { phone_number: phoneNumber } } : {}),
      description: lines
        .map((line) => `${line.quantity} x ${line.name}`)
        .join(", ")
        .slice(0, MAX_DESCRIPTION),
      return_url: `${this.settings.publicUrl}/orders/${reference}`,
      metadata: {
        items: lines
          .map((line) => `${line.product_id}:${line.quantity}`)
          .join(","),
      },
    })
    const order: Order = {
      reference,
      lines,
      amount,
      payment_id: payment.id,
      provider,
      status: payment.status,
      next_action: payment.next_action,
      created_at: payment.created_at,
      events: [],
    }
    this.orders.save(order)
    return order
  }

  async read(reference: string): Promise<Order> {
    const order = this.orders.find(reference)
    if (!order) throw new UnknownOrder(reference)
    const payment = await this.gateway.getPayment(order.payment_id)
    const current = {
      ...order,
      status: payment.status,
      next_action: payment.next_action,
    }
    this.orders.save(current)
    return current
  }

  list(): Order[] {
    return this.orders
      .all()
      .sort((left, right) => right.created_at.localeCompare(left.created_at))
  }
}
