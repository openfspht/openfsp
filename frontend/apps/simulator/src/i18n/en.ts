export const en = {
  product: "simulator",
  nav: {
    payments: "Payments",
    requests: "Requests",
    scenarios: "Scenarios",
    statements: "Statements",
  },
  operators: {
    mock_openfsp: "OpenFSP (mock)",
    mock_moncash: "MonCash (mock)",
    mock_natcash: "NatCash (mock)",
  },
  unreachable: "The simulator is not reachable.",
  payments: {
    title: "Payments",
    description:
      "What the simulated operators hold, as the gateway created them.",
    search: "Payment, order, phone or amount",
  },
  requests: {
    title: "Requests",
    description:
      "Every call the gateway made to the simulated operators, credentials redacted.",
    search: "Path or body",
  },
  scenarios: {
    title: "Scenarios",
    description: "How the next payments of each operator behave.",
  },
  statements: {
    title: "Statements",
    description: "Close a business day so the gateway can reconcile it.",
  },
}

export type Messages = typeof en
