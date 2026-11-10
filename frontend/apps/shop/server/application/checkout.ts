import type { Product } from "../domain/catalog"
import type { Order } from "../domain/order"
import type { Clock, Orders, PaymentGateway } from "./ports"

export class InvalidOrder extends Error {}

export class UnknownOrder extends Error {}

export interface CheckoutSettings {
  providers: string[]
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

  providers(): string[] {
    return this.settings.providers
  }

  async place(
    productId: string,
    phoneNumber: string,
    provider: string
  ): Promise<Order> {
    const product = this.catalog.find((candidate) => candidate.id === productId)
    if (
      !product ||
      !phoneNumber ||
      !this.settings.providers.includes(provider)
    ) {
      throw new InvalidOrder(
        "product_id, phone_number and a known provider are required"
      )
    }
    const reference = `SHOP-${this.clock.now().getTime().toString(36).toUpperCase()}`
    const payment = await this.gateway.createPayment({
      reference,
      amount: product.price,
      provider,
      payer: { phone_number: phoneNumber },
      description: product.name,
      return_url: `${this.settings.publicUrl}/orders/${reference}`,
      metadata: { product: product.id },
    })
    const order: Order = {
      reference,
      product: product.name,
      amount: product.price,
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
