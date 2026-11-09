import type { Money } from "@workspace/types"

export interface Product {
  id: string
  name: string
  description: string
  price: Money
}

export const catalog: Product[] = [
  { id: "coffee", name: "Coffee beans", description: "1 kg of roasted beans from Thiotte.", price: { amount: 125_000, currency: "HTG" } },
  { id: "mangoes", name: "Mangoes", description: "A crate of Francique mangoes.", price: { amount: 45_000, currency: "HTG" } },
  { id: "chocolate", name: "Chocolate", description: "Six bars of dark chocolate.", price: { amount: 98_000, currency: "HTG" } },
]
