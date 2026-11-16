import { useSyncExternalStore } from "react"

const STORAGE_KEY = "mache-lakou.cart"
const MAX_QUANTITY = 20

export type Cart = Record<string, number>

let cart: Cart = read()
const listeners = new Set<() => void>()

function read(): Cart {
  try {
    const stored = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? "{}")
    return typeof stored === "object" && stored ? (stored as Cart) : {}
  } catch {
    return {}
  }
}

function write(next: Cart) {
  cart = next
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(next))
  } catch {
    return listeners.forEach((listener) => listener())
  }
  listeners.forEach((listener) => listener())
}

const clamp = (quantity: number) => Math.min(Math.max(quantity, 0), MAX_QUANTITY)

export const cartStore = {
  set(productId: string, quantity: number) {
    const next = { ...cart, [productId]: clamp(quantity) }
    if (next[productId] === 0) delete next[productId]
    write(next)
  },
  add(productId: string) {
    cartStore.set(productId, (cart[productId] ?? 0) + 1)
  },
  clear() {
    write({})
  },
}

export function useCart() {
  const current = useSyncExternalStore(
    (listener) => {
      listeners.add(listener)
      return () => listeners.delete(listener)
    },
    () => cart
  )
  const count = Object.values(current).reduce((total, quantity) => total + quantity, 0)
  return { cart: current, count }
}
