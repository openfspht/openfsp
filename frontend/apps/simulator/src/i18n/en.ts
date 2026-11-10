export const en = {
  product: "simulator",
  nav: {
    activity: "Activity",
    requests: "Requests",
  },
  operators: {
    mock_openfsp: "OpenFSP (mock)",
    mock_moncash: "MonCash (mock)",
    mock_natcash: "NatCash (mock)",
  },
  unreachable: "The simulator is not reachable.",
  activity: {
    title: "Activity",
    description:
      "The payments the simulated operators hold. Choose how the next ones behave and close the days to reconcile.",
    search: "Payment, order, phone or amount",
  },
  requests: {
    title: "Requests",
    description:
      "Every call the gateway made to the simulated operators, credentials redacted.",
    search: "Path or body",
  },
}

export type Messages = typeof en
