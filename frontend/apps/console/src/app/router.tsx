import { createBrowserRouter, Navigate } from "react-router"

import { ActivityPage } from "@/features/activity/activity-page"
import { AuditPage } from "@/features/audit/audit-page"
import { ConfigurationPage } from "@/features/configuration/configuration-page"
import { KeysPage } from "@/features/keys/keys-page"
import { PaymentPage } from "@/features/payments/payment-page"
import { PaymentsPage } from "@/features/payments/payments-page"
import { ProvidersPage } from "@/features/providers/providers-page"
import { RequireSession } from "@/features/session/require-session"
import { SignInPage } from "@/features/session/sign-in-page"
import { ConsoleFrame } from "@/features/shell/console-frame"
import { StatementsPage } from "@/features/statements/statements-page"
import { WebhooksPage } from "@/features/webhooks/webhooks-page"

export const router = createBrowserRouter([
  { path: "/sign-in", element: <SignInPage /> },
  {
    element: <RequireSession />,
    children: [
      {
        element: <ConsoleFrame />,
        children: [
          { index: true, element: <Navigate replace to="/activity" /> },
          { path: "activity", element: <ActivityPage /> },
          { path: "payments", element: <PaymentsPage /> },
          { path: "payments/:id", element: <PaymentPage /> },
          { path: "providers", element: <ProvidersPage /> },
          { path: "statements", element: <StatementsPage /> },
          { path: "keys", element: <KeysPage /> },
          { path: "webhooks", element: <WebhooksPage /> },
          { path: "audit", element: <AuditPage /> },
          { path: "configuration", element: <ConfigurationPage /> },
          { path: "*", element: <Navigate replace to="/activity" /> },
        ],
      },
    ],
  },
])
