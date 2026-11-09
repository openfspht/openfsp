import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { AppHeader, ErrorText, LabeledInput, OptionSelect, OrderReturn, OrdersList, ProductCard, ShopLayout } from "@workspace/design-system"
import { useState } from "react"

import { shop } from "@/api"

const EVERY_SECOND = 1000
const returnedReference = window.location.pathname.match(/^\/orders\/([^/]+)$/)?.[1]

function Returned({ reference }: { reference: string }) {
  const order = useQuery({ queryKey: ["order", reference], queryFn: () => shop.read(decodeURIComponent(reference)), refetchInterval: EVERY_SECOND })
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

function Catalog() {
  const queryClient = useQueryClient()
  const [phoneNumber, setPhoneNumber] = useState("+50934567890")
  const [provider, setProvider] = useState("mock_alpha")
  const products = useQuery({ queryKey: ["products"], queryFn: shop.products })
  const orders = useQuery({ queryKey: ["orders"], queryFn: shop.orders, refetchInterval: EVERY_SECOND })
  const order = useMutation({
    mutationFn: (productId: string) => shop.order(productId, phoneNumber, provider),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["orders"] }),
  })

  return (
    <>
      <div className="flex flex-wrap items-end gap-8">
        <div className="w-72">
          <LabeledInput
            id="phone"
            label="Phone number"
            hint="E.164, the number that approves the payment."
            value={phoneNumber}
            onChange={(event) => setPhoneNumber(event.target.value)}
          />
        </div>
        <OptionSelect value={provider} options={products.data?.providers ?? [provider]} onChange={setProvider} />
      </div>
      {order.isError && <ErrorText>{order.error.message}</ErrorText>}
      <div className="grid gap-6 sm:grid-cols-3">
        {products.data?.data.map((product) => (
          <ProductCard
            key={product.id}
            name={product.name}
            description={product.description}
            price={product.price}
            buyLabel={`Pay with ${provider}`}
            disabled={order.isPending}
            onBuy={() => order.mutate(product.id)}
          />
        ))}
      </div>
      <OrdersList orders={orders.data?.data ?? []} />
    </>
  )
}

export function App() {
  return (
    <ShopLayout header={<AppHeader title="demo shop" environment="test" />}>
      {returnedReference ? <Returned reference={returnedReference} /> : <Catalog />}
    </ShopLayout>
  )
}
