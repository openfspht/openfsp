import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { AppHeader, ErrorText, LabeledInput, OrdersList, ProductCard, ShopLayout } from "@workspace/design-system"
import { useState } from "react"

import { shop } from "@/api"

export function App() {
  const queryClient = useQueryClient()
  const [phoneNumber, setPhoneNumber] = useState("+50934567890")
  const products = useQuery({ queryKey: ["products"], queryFn: shop.products })
  const orders = useQuery({ queryKey: ["orders"], queryFn: shop.orders, refetchInterval: 1000 })
  const order = useMutation({
    mutationFn: (productId: string) => shop.order(productId, phoneNumber),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["orders"] }),
  })

  return (
    <ShopLayout header={<AppHeader title="demo shop" environment="test" />}>
      <div className="max-w-sm">
        <LabeledInput
          id="phone"
          label="Phone number"
          hint="E.164, the number that approves the payment."
          value={phoneNumber}
          onChange={(event) => setPhoneNumber(event.target.value)}
        />
      </div>
      {order.isError && <ErrorText>{order.error.message}</ErrorText>}
      <div className="grid gap-6 sm:grid-cols-3">
        {products.data?.data.map((product) => (
          <ProductCard
            key={product.id}
            name={product.name}
            description={product.description}
            price={product.price}
            disabled={order.isPending}
            onBuy={() => order.mutate(product.id)}
          />
        ))}
      </div>
      <OrdersList orders={orders.data?.data ?? []} />
    </ShopLayout>
  )
}
