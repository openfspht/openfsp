import {
  ErrorText,
  PaymentDetail,
  PaymentsTable,
} from "@workspace/design-system"

import type { PaymentsState } from "./use-payments"

export function PaymentsSection({ payments }: { payments: PaymentsState }) {
  return (
    <>
      {payments.unreachable && (
        <ErrorText>The gateway is not reachable.</ErrorText>
      )}
      <PaymentsTable
        payments={payments.list}
        selectedId={payments.activeId}
        onSelect={payments.select}
      />
    </>
  )
}

export function PaymentDetailSection({
  payments,
}: {
  payments: PaymentsState
}) {
  if (!payments.detail) return null
  return (
    <PaymentDetail
      detail={payments.detail}
      onAttest={payments.attest.mutate}
      attesting={payments.attest.isPending}
    />
  )
}
