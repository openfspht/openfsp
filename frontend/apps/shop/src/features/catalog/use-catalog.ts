import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { useState } from "react"

import { shop } from "@/api/shop"

const LIVE = 1000
const ORDERS = ["orders"] as const

export function useCatalog() {
  const queryClient = useQueryClient()
  const [phoneNumber, setPhoneNumber] = useState("+50934567890")
  const [provider, setProvider] = useState("mock_openfsp")
  const products = useQuery({ queryKey: ["products"], queryFn: shop.products })
  const orders = useQuery({
    queryKey: ORDERS,
    queryFn: shop.orders,
    refetchInterval: LIVE,
  })
  const order = useMutation({
    mutationFn: (productId: string) =>
      shop.order(productId, phoneNumber, provider),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ORDERS }),
  })
  return {
    phoneNumber,
    setPhoneNumber,
    provider,
    setProvider,
    products,
    orders,
    order,
  }
}
