import { Link, useSearchParams } from "react-router"

import { ProductArt } from "@/art/product-art"
import { useI18n } from "@/i18n/i18n-provider"
import { Price, TornEdge } from "@/ui/paper"

import { AddButton } from "./add-button"
import { AISLES, PAPER } from "./aisle"
import { useCatalog } from "./use-catalog"

const FEATURED_DRAW = Math.random()
const WIDE = 2

export function HomePage() {
  const { t } = useI18n()
  const { products, text, catalog } = useCatalog()
  const [params, setParams] = useSearchParams()
  const aisle = params.get("aisle")
  const featured = products[Math.floor(FEATURED_DRAW * products.length)]
  const shown = products.filter((product) => (aisle ? product.aisle === aisle : product.id !== featured?.id))
  return (
    <>
      {featured && (
        <section className={`hero paper ${PAPER[featured.aisle]}`} aria-labelledby="featured-name">
          <div className="shop-wrap hero__grid">
            <div className="hero__copy">
              <h1 id="featured-name" className="display hero__name">
                {text(featured.name)}
              </h1>
              <p className="hero__origin">
                {t.featured} · {text(featured.origin)}
              </p>
              <p className="hero__text">{text(featured.description)}</p>
            </div>
            <div className="hero__object">
              <Link to={`/products/${featured.id}`} className="hero__art" aria-label={t.view(text(featured.name))}>
                <ProductArt product={featured.id} title={text(featured.name)} className="hero__svg" />
              </Link>
              <div className={`tag paper ${featured.aisle === "pantry" ? "" : "paper--sun"}`}>
                <Price value={featured.price} className="tag__price" />
                <span className="tag__unit">{text(featured.unit)}</span>
                <AddButton productId={featured.id} />
              </div>
            </div>
          </div>
          <TornEdge flip className="hero__edge" variant={1} />
        </section>
      )}
      <section className="shop-wrap aisles" aria-label={t.nav.shop}>
        <div className="aisles__chips" role="toolbar">
          <button type="button" className="strip paper" aria-pressed={!aisle} onClick={() => setParams({})}>
            {t.aisles.all}
            <TornEdge flip className="strip__edge" />
          </button>
          {AISLES.map((name, index) => (
            <button key={name} type="button" className={`strip paper ${PAPER[name]}`} aria-pressed={aisle === name} onClick={() => setParams({ aisle: name })}>
              {t.aisles[name]}
              <TornEdge flip variant={index} className="strip__edge" />
            </button>
          ))}
        </div>
        {catalog.isError && <p className="error">{t.checkout.unreachable}</p>}
        <ul className="stalls">
          {shown.map((product, index) => (
            <li key={product.id} className={`stall ${index === WIDE ? "stall--wide" : ""}`}>
              <Link to={`/products/${product.id}`} className={`stall__patch paper ${PAPER[product.aisle]} ${index % 2 ? "tilt-r" : "tilt-l"}`}>
                <ProductArt product={product.id} title={text(product.name)} className="stall__art" />
              </Link>
              <div className="stall__body">
                <h2 className="display stall__name">
                  <Link to={`/products/${product.id}`}>{text(product.name)}</Link>
                </h2>
                <p className="soft stall__origin">{text(product.origin)}</p>
                <div className="stall__buy">
                  <Price value={product.price} className="stall__price" />
                  <span className="soft">{text(product.unit)}</span>
                </div>
                <AddButton productId={product.id} className="btn--small" />
              </div>
            </li>
          ))}
        </ul>
      </section>
    </>
  )
}
