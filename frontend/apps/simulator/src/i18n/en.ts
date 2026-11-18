export const en = {
    product: "Simulator",
    nav: {
        activity: "Activity",
        requests: "Requests",
    },
    operators: {
        mock_openfsp: "OpenFSP (mock)",
        mock_moncash: "MonCash (mock)",
        mock_natcash: "NatCash (mock)",
    },
    unreachable: "Unable to reach the simulator. Check your connection and try again.",
    activity: {
        title: "Activity",
        description:
            "View payments held by simulated payment services, and configure scenarios for subsequent payments.",
        search: "Payment, order, phone or amount",
    },
    requests: {
        title: "Requests",
        description:
            "Log of calls received by the simulated payment services. Authentication credentials are redacted.",
        search: "Path or body",
    },
    operator: "Payment service",
    allOperators: "All services",
    payments: {
        payment: "Payment",
        scenario: "Scenario",
        amount: "Amount",
        state: "Status",
        exchanges: (callbacks: number, lookups: number) =>
            `${callbacks} callback${callbacks === 1 ? "" : "s"}, ${lookups} lookup${lookups === 1 ? "" : "s"}`,
        empty: "The simulator has not received any payments.",
        reported: (amount: string) => `reported amount: ${amount}`,
        sendCallback: "Send callback",
        settleDescription:
            "Set the outcome that the simulated payment service will return for this payment.",
        callbackDescription: "Resend the simulated payment service's callback for this payment.",
        noCallback: "No callback URL is associated with this payment.",
    },
    resolution: {
        succeed: "Simulate success",
        decline: "Simulate decline",
        expire: "Simulate expiry",
    },
    journal: {
        received: "Received",
        request: "Request",
        empty: "No request yet.",
        status: "HTTP status",
        duration: (ms: number) => `${ms} ms`,
    },
    scenarios: {
        title: "Scenarios",
        description:
            "Configure the behaviour of subsequent payments for each simulated service. In automatic mode, scenarios are applied to new orders in a repeating sequence.",
        next: "Next payments",
        automatic: "Automatic",
        automaticLabel: (operator: string) => `Enable scenario rotation for ${operator}`,
        rotating: (count: number) => `Rotating through ${count} scenarios`,
        refused: "The change was not applied. Check the scenario settings and try again.",
    },
    reset: {
        label: "Reset",
        title: "Reset the simulator?",
        description:
            "This action deletes payments, scenarios and requests for all users of this simulator.",
        confirm: "Reset",
    },
}

export type Messages = typeof en
