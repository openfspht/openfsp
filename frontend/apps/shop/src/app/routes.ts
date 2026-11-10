export type Route = { page: "catalog" } | { page: "order"; reference: string }

export function routeOf(pathname: string): Route {
  const order = pathname.match(/^\/orders\/([^/]+)$/)
  return order?.[1]
    ? { page: "order", reference: decodeURIComponent(order[1]) }
    : { page: "catalog" }
}
