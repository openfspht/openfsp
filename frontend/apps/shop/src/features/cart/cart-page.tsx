import { Minus, Plus, X } from "lucide-react"
import { Link } from "react-router"

import { ProductArt } from "@/art/product-art"
import { PAPER } from "@/features/catalog/aisle"
import { useCatalog } from "@/features/catalog/use-catalog"
import { useI18n } from "@/i18n/i18n-provider"
import { Price, Stations } from "@/ui/paper"

import { cartStore, useCart } from "./cart"

export function useCartLines() {
  const { cart } = useCart()
  const { find } = useCatalog()
  const lines = Object.entries(cart).flatMap(([id, quantity]) => {
    const product = find(id)
    return product ? [{ product, quantity }] : []
  })
  const total = {
    amount: lines.reduce((sum, line) => sum + line.product.price.amount * line.quantity, 0),
    currency: "HTG" as const,
  }
  return { lines, total }
}

export function CartPage() {
  const { t } = useI18n()
  const { text, catalog } = useCatalog()
  const { lines, total } = useCartLines()
  if (catalog.isPending) return null
  return (
    <section className="shop-wrap page">
      <Stations current="cart" labels={t.stations} />
      <h1 className="display page__title">{t.cart.title}</h1>
      {lines.length === 0 ? (
        <div className="empty paper">
          <p>{t.cart.empty}</p>
          <Link to="/" className="btn">
            {t.cart.browse}
          </Link>
        </div>
      ) : (
        <div className="cart">
          <ul className="cart__lines">
            {lines.map(({ product, quantity }) => (
              <li key={product.id} className="cart__line paper">
                <Link to={`/products/${product.id}`} className={`cart__thumb ${PAPER[product.aisle]}`}>
                  <ProductArt product={product.id} title={text(product.name)} className="cart__art" />
                </Link>
                <div className="cart__what">
                  <Link to={`/products/${product.id}`} className="display cart__name">
                    {text(product.name)}
                  </Link>
                  <span className="soft">{text(product.unit)}</span>
                </div>
                <div className="stepper" role="group" aria-label={`${t.cart.quantity}: ${text(product.name)}`}>
                  <button type="button" aria-label={t.cart.decrease(text(product.name))} onClick={() => cartStore.set(product.id, quantity - 1)}>
                    <Minus size={16} />
                  </button>
                  <output aria-live="polite">{quantity}</output>
                  <button type="button" aria-label={t.cart.increase(text(product.name))} disabled={quantity >= 20} onClick={() => cartStore.set(product.id, quantity + 1)}>
                    <Plus size={16} />
                  </button>
                </div>
                <Price value={{ amount: product.price.amount * quantity, currency: product.price.currency }} className="cart__amount" />
                <button type="button" className="cart__remove" aria-label={`${t.cart.remove}: ${text(product.name)}`} onClick={() => cartStore.set(product.id, 0)}>
                  <X size={18} />
                </button>
              </li>
            ))}
          </ul>
          <aside className="summary paper paper--sun">
            <p className="summary__label">{t.cart.total}</p>
            <Price value={total} className="summary__total" />
            <Link to="/checkout" className="btn summary__go">
              {t.cart.checkout}
            </Link>
          </aside>
        </div>
      )}
    </section>
  )
}
