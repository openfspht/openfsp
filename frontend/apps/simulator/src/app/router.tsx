import { createBrowserRouter, Navigate } from "react-router"

import { ActivityPage } from "@/features/activity/activity-page"
import { RequestsPage } from "@/features/requests/requests-page"
import { SimulatorFrame } from "@/features/shell/simulator-frame"

export const router = createBrowserRouter([
    {
        element: <SimulatorFrame />,
        children: [
            { index: true, element: <Navigate replace to="/activity" /> },
            { path: "activity", element: <ActivityPage /> },
            { path: "requests", element: <RequestsPage /> },
            { path: "*", element: <Navigate replace to="/activity" /> },
        ],
    },
])
