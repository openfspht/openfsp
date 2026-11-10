import { AppHeader, ConsoleLayout } from "@workspace/design-system"

import { KeysSection } from "@/features/keys/keys-section"
import {
  PaymentDetailSection,
  PaymentsSection,
} from "@/features/payments/payments-sections"
import { usePayments } from "@/features/payments/use-payments"
import { SimulatorSection } from "@/features/simulator/simulator-section"
import { GatewayStatementSection } from "@/features/statements/gateway-statement-section"

export function App() {
  const payments = usePayments()
  return (
    <ConsoleLayout
      header={<AppHeader title="console" environment="test" />}
      main={
        <>
          <PaymentsSection payments={payments} />
          <GatewayStatementSection />
          <KeysSection />
        </>
      }
      aside={
        <>
          <SimulatorSection />
          <PaymentDetailSection payments={payments} />
        </>
      }
    />
  )
}
