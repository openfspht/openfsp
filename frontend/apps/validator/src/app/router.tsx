import { createBrowserRouter, Navigate } from "react-router"

import { HomePage } from "@/features/home/home-page"
import { ReportPage } from "@/features/report/report-page"
import { RunPage } from "@/features/run/run-page"
import { ValidatorFrame } from "@/features/shell/validator-frame"

export const router = createBrowserRouter([
  {
    element: <ValidatorFrame />,
    children: [
      { index: true, element: <HomePage /> },
      { path: "runs/:id", element: <RunPage /> },
      { path: "reports/:id", element: <ReportPage /> },
      { path: "*", element: <Navigate replace to="/" /> },
    ],
  },
])
