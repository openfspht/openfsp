import { createBrowserRouter, Navigate } from "react-router"

import { PaymentsPage } from "@/features/payments/payments-page"
import { RequestsPage } from "@/features/requests/requests-page"
import { ScenariosPage } from "@/features/scenarios/scenarios-page"
import { SimulatorFrame } from "@/features/shell/simulator-frame"
import { StatementsPage } from "@/features/statements/statements-page"

export const router = createBrowserRouter([
  {
    element: <SimulatorFrame />,
    children: [
      { index: true, element: <Navigate replace to="/payments" /> },
      { path: "payments", element: <PaymentsPage /> },
      { path: "requests", element: <RequestsPage /> },
      { path: "scenarios", element: <ScenariosPage /> },
      { path: "statements", element: <StatementsPage /> },
      { path: "*", element: <Navigate replace to="/payments" /> },
    ],
  },
])
