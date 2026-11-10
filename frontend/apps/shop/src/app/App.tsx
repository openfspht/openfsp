import { AppHeader, ShopLayout } from "@workspace/design-system"

import { CatalogPage } from "@/features/catalog/catalog-page"
import { OrderReturnPage } from "@/features/orders/order-return-page"

import { routeOf } from "./routes"

export function App() {
  const route = routeOf(window.location.pathname)
  return (
    <ShopLayout header={<AppHeader title="demo shop" environment="test" />}>
      {route.page === "order" ? (
        <OrderReturnPage reference={route.reference} />
      ) : (
        <CatalogPage />
      )}
    </ShopLayout>
  )
}
