import { useQuery } from "@tanstack/react-query"
import { useCopy } from "@workspace/design-system"
import { BadgeCheck } from "lucide-react"
import { Link, useParams } from "react-router"

import { shop, ShopError } from "@/api/shop"
import { useCatalog } from "@/features/catalog/use-catalog"
import { useI18n } from "@/i18n/i18n-provider"
import { Price, Stations, TornEdge, type Station } from "@/ui/paper"

const LIVE = 2000

const STAMP: Record<string, string> = {
  pending: "paper--sun",
  succeeded: "paper--leaf",
  failed: "paper--hibiscus",
  expired: "paper--hibiscus",
  canceled: "paper--hibiscus",
}

export function OrderPage() {
  const { reference = "" } = useParams()
  const { t } = useI18n()
  const { locale } = useCopy()
  const { find, text, providers } = useCatalog()
  const order = useQuery({
    queryKey: ["order", reference],
    queryFn: () => shop.read(reference),
    refetchInterval: (query) => (query.state.data?.status === "pending" ? LIVE : false),
    retry: (count, error) => !(error instanceof ShopError && error.status === 404) && count < 3,
  })
  if (order.isPending) return null
  if (!order.data)
    return (
      <section className="shop-wrap page">
        <p className="error">{order.error instanceof ShopError && order.error.status === 404 ? t.order.notFound : t.checkout.unreachable}</p>
        <Link to="/">{t.back}</Link>
      </section>
    )
  const current = order.data
  const providerName = providers.find((provider) => provider.id === current.provider)?.name ?? current.provider
  const station: Station = current.status === "succeeded" ? "done" : current.status === "pending" ? "approve" : "pay"
  return (
    <section className="shop-wrap page">
      <Stations current={station} labels={t.stations} />
      <h1 className="display page__title">{t.order.title(current.reference)}</h1>
      <div className="order">
        <div className={`stamp paper ${STAMP[current.status]}`} role="status">
          <p className="display stamp__status">{t.order.status[current.status]}</p>
          <p className="stamp__hint">{t.order.hint[current.status]}</p>
          {current.status === "pending" && current.next_action?.type === "redirect" && (
            <a className="btn" href={current.next_action.redirect_url}>
              {t.order.continueTo(providerName)}
            </a>
          )}
          {current.status === "pending" && current.next_action?.type === "payer_approval" && <p className="stamp__hint">{t.order.approve}</p>}
          <p className="stamp__source">{t.order.source}</p>
        </div>
        <aside className="receipt paper">
          <ul className="receipt__lines">
            {current.lines.map((line) => {
              const product = find(line.product_id)
              return (
                <li key={line.product_id}>
                  <span>
                    {line.quantity} × {product ? text(product.name) : line.name}
                  </span>
                  <Price value={{ amount: line.unit_price.amount * line.quantity, currency: line.unit_price.currency }} />
                </li>
              )
            })}
          </ul>
          <p className="receipt__total seam">
            <span>{t.cart.total}</span>
            <Price value={current.amount} />
          </p>
          <TornEdge className="receipt__edge" variant={1} />
          <p className="soft receipt__meta">
            {t.order.paidWith} {providerName} · {new Date(current.created_at).toLocaleString(locale)}
          </p>
        </aside>
      </div>
      <section className="webhooks" aria-labelledby="webhooks-title">
        <h2 id="webhooks-title" className="display webhooks__title">
          {t.order.webhooks}
        </h2>
        <p className="soft">{t.order.webhooksHint}</p>
        {current.events.length === 0 ? (
          <p className="soft">{t.order.noWebhooks}</p>
        ) : (
          <ol className="webhooks__list">
            {current.events.map((event) => (
              <li key={event.id} className="paper">
                <BadgeCheck size={18} aria-hidden="true" className="webhooks__check" />
                <span className="webhooks__seq">#{event.sequence}</span>
                <code>{event.type}</code>
                <time className="soft" dateTime={event.received_at}>
                  {new Date(event.received_at).toLocaleTimeString(locale)}
                </time>
              </li>
            ))}
          </ol>
        )}
      </section>
    </section>
  )
}
