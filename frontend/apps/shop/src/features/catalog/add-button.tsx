import { Check, Plus } from "lucide-react"

import { cartStore, useCart } from "@/features/cart/cart"
import { useI18n } from "@/i18n/i18n-provider"

export function AddButton({ productId, className = "" }: { productId: string; className?: string }) {
  const { t } = useI18n()
  const { cart } = useCart()
  const inCart = cart[productId] ?? 0
  return (
    <button type="button" className={`btn ${className}`} onClick={() => cartStore.add(productId)}>
      {inCart > 0 ? <Check size={18} aria-hidden="true" /> : <Plus size={18} aria-hidden="true" />}
      {inCart > 0 ? `${t.added} · ${inCart}` : t.add}
    </button>
  )
}
