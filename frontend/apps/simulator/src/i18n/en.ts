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
  operator: "Operator",
  allOperators: "All operators",
  payments: {
    payment: "Payment",
    scenario: "Scenario",
    amount: "Amount",
    state: "Status",
    exchanges: (callbacks: number, lookups: number) =>
      `${callbacks} callback${callbacks === 1 ? "" : "s"}, ${lookups} lookup${lookups === 1 ? "" : "s"}`,
    empty: "No payment reached the simulator yet.",
    reported: (amount: string) => `reported ${amount}`,
    sendCallback: "Send callback",
    settleDescription: "Choose what the operator answers for this payment.",
    callbackDescription: "Send the operator callback for this payment again.",
    noCallback: "This payment has no callback URL.",
  },
  resolution: { succeed: "Succeed", decline: "Decline", expire: "Expire" },
  journal: {
    received: "Received",
    request: "Request",
    empty: "No request yet.",
    status: "Status",
    duration: (ms: number) => `${ms} ms`,
  },
  scenarios: {
    title: "Scenarios",
    description:
      "How the next payments of each operator behave. Auto gives each new order the next scenario in turn.",
    next: "Next payments",
    automatic: "Auto",
    automaticLabel: (operator: string) => `Rotate the scenarios of ${operator}`,
    rotating: (count: number) => `Rotating through ${count} scenarios`,
    refused: "The simulator did not apply the change.",
  },
  statements: {
    title: "Close a day",
    description:
      "Mark a business day as complete so the gateway can reconcile it. Days follow Haiti time.",
    day: "Business day",
    close: "Close the day",
    closed: "Already closed",
    future: "This day has not started yet.",
    closedDays: (days: string) => `Closed: ${days}`,
    none: "No day closed yet.",
  },
  reset: {
    label: "Reset",
    title: "Reset the simulator?",
    description:
      "Payments, scenarios, requests and closed days are erased for everyone using this simulator.",
    confirm: "Reset",
  },
}

export type Messages = typeof en
