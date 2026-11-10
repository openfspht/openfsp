import { formatMoney, useCopy } from "@workspace/design-system"
import { formatPhoneNumber } from "@workspace/openfsp-client/phone"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { useState } from "react"
import { Navigate, useNavigate } from "react-router"

import { shop } from "@/api/shop"
import { cartStore } from "@/features/cart/cart"
import { useCartLines } from "@/features/cart/cart-page"
import { useCatalog } from "@/features/catalog/use-catalog"
import { useI18n } from "@/i18n/i18n-provider"
import { Price, Stations, TornEdge } from "@/ui/paper"

function ProviderLogo({ name, url }: { name: string; url: string | null }) {
  const [broken, setBroken] = useState(false)
  return (
    <span className="provider__logo" aria-hidden="true">
      {url && !broken ? <img src={url} alt="" loading="lazy" onError={() => setBroken(true)} /> : name.slice(0, 1)}
    </span>
  )
}

export function CheckoutPage() {
  const { t } = useI18n()
  const { locale } = useCopy()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { providers, text, catalog } = useCatalog()
  const { lines, total } = useCartLines()
  const [chosen, setChosen] = useState<string>()
  const [phone, setPhone] = useState("")
  const provider = chosen ?? providers[0]?.id
  const pay = useMutation({
    mutationFn: () =>
      shop.order(
        lines.map((line) => ({ product_id: line.product.id, quantity: line.quantity })),
        phone.trim(),
        provider ?? ""
      ),
    onSuccess: (order) => {
      cartStore.clear()
      void queryClient.invalidateQueries({ queryKey: ["orders"] })
      if (order.next_action?.type === "redirect") window.location.assign(order.next_action.redirect_url)
      else navigate(`/orders/${encodeURIComponent(order.reference)}`)
    },
  })
  if (catalog.isPending) return null
  if (lines.length === 0 && !pay.isSuccess) return <Navigate replace to="/cart" />
  return (
    <section className="shop-wrap page">
      <Stations current="pay" labels={t.stations} />
      <h1 className="display page__title">{t.checkout.title}</h1>
      <div className="checkout">
        <form
          className="checkout__form"
          onSubmit={(event) => {
            event.preventDefault()
            pay.mutate()
          }}
        >
          <fieldset className="providers">
            <legend className="display providers__legend">{t.checkout.provider}</legend>
            {providers.length === 0 && <p className="error">{catalog.isError ? t.checkout.unreachable : t.checkout.noProvider}</p>}
            {providers.map((option) => (
              <label key={option.id} className={`provider paper ${option.id === provider ? "paper--sun" : ""}`} data-checked={option.id === provider}>
                <input type="radio" name="provider" value={option.id} checked={option.id === provider} onChange={() => setChosen(option.id)} />
                <ProviderLogo name={option.name} url={option.logo_url} />
                <span className="provider__name">{option.name}</span>
              </label>
            ))}
          </fieldset>
          <div className="field">
            <label htmlFor="phone">{t.checkout.phone}</label>
            <input id="phone" type="tel" inputMode="tel" autoComplete="tel" placeholder="+509 40 00 0000" value={phone} onChange={(event) => setPhone(event.target.value)} onBlur={() => setPhone((value) => (value.trim() ? formatPhoneNumber(value) : ""))} />
            <small>{t.checkout.phoneHint}</small>
          </div>
          {pay.isError && <p className="error" role="alert">{pay.error.message}</p>}
          <button type="submit" className="btn checkout__pay" disabled={!provider || pay.isPending}>
            {pay.isPending ? t.checkout.paying : t.checkout.pay(formatMoney(total, locale))}
          </button>
          <p className="soft checkout__note">{t.checkout.secured}</p>
        </form>
        <aside className="receipt paper">
          <ul className="receipt__lines">
            {lines.map(({ product, quantity }) => (
              <li key={product.id}>
                <span>
                  {quantity} × {text(product.name)}
                </span>
                <Price value={{ amount: product.price.amount * quantity, currency: product.price.currency }} />
              </li>
            ))}
          </ul>
          <p className="receipt__total seam">
            <span>{t.cart.total}</span>
            <Price value={total} />
          </p>
          <TornEdge className="receipt__edge" />
        </aside>
      </div>
    </section>
  )
}
