import { AppHeader, ConsoleLayout } from "@workspace/design-system"

import { KeysSection } from "@/features/keys/keys-section"
import {
  PaymentDetailSection,
  PaymentsSection,
} from "@/features/payments/payments-sections"
import { usePayments } from "@/features/payments/use-payments"
import { ProvidersSection } from "@/features/providers/providers-section"
import { SessionGate } from "@/features/session/session-gate"
import { GatewayStatementSection } from "@/features/statements/gateway-statement-section"

export function App() {
  return <SessionGate>{(signOut) => <Dashboard onSignOut={signOut} />}</SessionGate>
}

function Dashboard({ onSignOut }: { onSignOut: () => void }) {
  const payments = usePayments()
  return (
    <ConsoleLayout
      header={
        <AppHeader title="console" environment="test" onSignOut={onSignOut} />
      }
      main={
        <>
          <PaymentsSection payments={payments} />
          <GatewayStatementSection />
          <KeysSection />
        </>
      }
      aside={
        <>
          <PaymentDetailSection payments={payments} />
          <ProvidersSection />
        </>
      }
    />
  )
}
