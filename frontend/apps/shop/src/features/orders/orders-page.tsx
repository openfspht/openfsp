import { useQuery } from "@tanstack/react-query"
import { StatusIndicator, useCopy } from "@workspace/design-system"
import { Link, useSearchParams } from "react-router"

import { shop } from "@/api/shop"
import { useCatalog } from "@/features/catalog/use-catalog"
import { useI18n } from "@/i18n/i18n-provider"
import { Price } from "@/ui/paper"

const LIVE = 3000

export function OrdersPage() {
  const { t } = useI18n()
  const { locale } = useCopy()
  const { find, text, providers } = useCatalog()
  const [params, setParams] = useSearchParams()
  const page = Math.max(Number(params.get("page")) || 0, 0)
  const orders = useQuery({
    queryKey: ["orders", page],
    queryFn: () => shop.orders(page),
    refetchInterval: LIVE,
    placeholderData: (previous) => previous,
  })
  const list = orders.data?.data ?? []
  const info = orders.data?.page
  const go = (next: number) => setParams(next > 0 ? { page: String(next) } : {})
  return (
    <section className="shop-wrap page">
      <h1 className="display page__title">{t.orders.title}</h1>
      {orders.isError && <p className="error">{t.checkout.unreachable}</p>}
      {list.length === 0 && orders.isSuccess ? (
        <div className="empty paper">
          <p>{t.orders.empty}</p>
          <Link to="/" className="btn">
            {t.cart.browse}
          </Link>
        </div>
      ) : (
        <ul className="ledger">
          {list.map((order) => (
            <li key={order.reference}>
              <Link to={`/orders/${encodeURIComponent(order.reference)}`} className="ledger__row paper">
                <span className="ledger__ref">{order.reference}</span>
                <span className="ledger__what">
                  {order.lines.map((line) => `${line.quantity} × ${find(line.product_id) ? text(find(line.product_id)!.name) : line.name}`).join(", ")}
                </span>
                <span className="soft ledger__provider">{providers.find((provider) => provider.id === order.provider)?.name ?? order.provider}</span>
                <span className="soft">{new Date(order.created_at).toLocaleString(locale)}</span>
                <Price value={order.amount} className="ledger__amount" />
                <StatusIndicator status={order.status} />
              </Link>
            </li>
          ))}
        </ul>
      )}
      {info && info.total_pages > 1 && (
        <nav className="pager" aria-label={t.orders.title}>
          <button type="button" className="btn btn--ghost btn--small" disabled={page <= 0} onClick={() => go(page - 1)}>
            {t.orders.previous}
          </button>
          <span>{t.orders.page(page + 1, info.total_pages)}</span>
          <button type="button" className="btn btn--ghost btn--small" disabled={page + 1 >= info.total_pages} onClick={() => go(page + 1)}>
            {t.orders.next}
          </button>
        </nav>
      )}
    </section>
  )
}
