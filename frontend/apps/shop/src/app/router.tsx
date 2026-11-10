import { createBrowserRouter, Navigate } from "react-router"

import { CartPage } from "@/features/cart/cart-page"
import { HomePage } from "@/features/catalog/home-page"
import { ProductPage } from "@/features/catalog/product-page"
import { CheckoutPage } from "@/features/checkout/checkout-page"
import { OrderPage } from "@/features/orders/order-page"
import { OrdersPage } from "@/features/orders/orders-page"
import { ShopFrame } from "@/features/shell/shop-frame"

export const router = createBrowserRouter([
  {
    element: <ShopFrame />,
    children: [
      { index: true, element: <HomePage /> },
      { path: "products/:id", element: <ProductPage /> },
      { path: "cart", element: <CartPage /> },
      { path: "checkout", element: <CheckoutPage /> },
      { path: "orders", element: <OrdersPage /> },
      { path: "orders/:reference", element: <OrderPage /> },
      { path: "*", element: <Navigate replace to="/" /> },
    ],
  },
])
