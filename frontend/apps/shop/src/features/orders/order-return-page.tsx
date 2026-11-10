import { useQuery } from "@tanstack/react-query"
import { ErrorText, OrderReturn } from "@workspace/design-system"

import { shop } from "@/api/shop"

const LIVE = 1000

export function OrderReturnPage({ reference }: { reference: string }) {
  const order = useQuery({
    queryKey: ["order", reference],
    queryFn: () => shop.read(reference),
    refetchInterval: LIVE,
  })
  return (
    <>
      {order.isError && <ErrorText>{order.error.message}</ErrorText>}
      {order.data && <OrderReturn order={order.data} />}
      <a className="text-sm underline underline-offset-4" href="/">
        Back to the shop
      </a>
    </>
  )
}
