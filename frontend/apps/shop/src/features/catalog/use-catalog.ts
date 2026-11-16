import { useQuery } from "@tanstack/react-query"

import { shop, type Product, type Text } from "@/api/shop"
import { useI18n } from "@/i18n/i18n-provider"

export function useCatalog() {
  const { locale } = useI18n()
  const catalog = useQuery({
    queryKey: ["catalog"],
    queryFn: shop.catalog,
    staleTime: 60_000,
  })
  const products = catalog.data?.data ?? []
  const text = (value: Text) => value[locale]
  const find = (id: string): Product | undefined =>
    products.find((product) => product.id === id)
  return {
    catalog,
    products,
    providers: catalog.data?.providers ?? [],
    text,
    find,
  }
}
