import {
  ErrorText,
  LabeledInput,
  OptionSelect,
  OrdersList,
  ProductCard,
} from "@workspace/design-system"

import { useCatalog } from "./use-catalog"

export function CatalogPage() {
  const catalog = useCatalog()
  return (
    <>
      <div className="flex flex-wrap items-end gap-8">
        <div className="w-72">
          <LabeledInput
            id="phone"
            label="Phone number"
            hint="E.164, the number that approves the payment."
            value={catalog.phoneNumber}
            onChange={(event) => catalog.setPhoneNumber(event.target.value)}
          />
        </div>
        <OptionSelect
          value={catalog.provider}
          options={catalog.products.data?.providers ?? [catalog.provider]}
          onChange={catalog.setProvider}
        />
      </div>
      {catalog.order.isError && (
        <ErrorText>{catalog.order.error.message}</ErrorText>
      )}
      <div className="grid gap-6 sm:grid-cols-3">
        {catalog.products.data?.data.map((product) => (
          <ProductCard
            key={product.id}
            name={product.name}
            description={product.description}
            price={product.price}
            buyLabel={`Pay with ${catalog.provider}`}
            disabled={catalog.order.isPending}
            onBuy={() => catalog.order.mutate(product.id)}
          />
        ))}
      </div>
      <OrdersList orders={catalog.orders.data?.data ?? []} />
    </>
  )
}
