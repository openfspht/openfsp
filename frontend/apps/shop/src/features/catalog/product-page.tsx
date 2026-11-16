import { ArrowLeft } from "lucide-react"
import { Link, useParams } from "react-router"

import { ProductArt } from "@/art/product-art"
import { useI18n } from "@/i18n/i18n-provider"
import { Price } from "@/ui/paper"

import { AddButton } from "./add-button"
import { PAPER } from "./aisle"
import { useCatalog } from "./use-catalog"

export function ProductPage() {
  const { id = "" } = useParams()
  const { t } = useI18n()
  const { find, text, catalog } = useCatalog()
  const product = find(id)
  if (catalog.isPending) return null
  if (!product)
    return (
      <section className="shop-wrap page">
        <p className="error">{t.order.notFound}</p>
        <Link to="/">{t.back}</Link>
      </section>
    )
  return (
    <section className="shop-wrap page product">
      <Link to="/" className="back">
        <ArrowLeft size={16} aria-hidden="true" />
        {t.back}
      </Link>
      <div className="product__grid">
        <div className={`product__patch paper ${PAPER[product.aisle]} tilt-l`}>
          <ProductArt product={product.id} title={text(product.name)} className="product__art" />
        </div>
        <div className="product__copy">
          <h1 className="display product__name">{text(product.name)}</h1>
          <p className="product__text">{text(product.description)}</p>
          <dl className="product__facts">
            <dt>{t.aisle}</dt>
            <dd>{t.aisles[product.aisle]}</dd>
            <dt>{t.from}</dt>
            <dd>{text(product.origin)}</dd>
            <dt>{t.size}</dt>
            <dd>{text(product.unit)}</dd>
          </dl>
          <div className="product__buy">
            <Price value={product.price} className="product__price" />
            <AddButton productId={product.id} />
          </div>
        </div>
      </div>
    </section>
  )
}
