import { StrictMode } from "react"
import { createRoot } from "react-dom/client"
import { RouterProvider } from "react-router"

import "@workspace/design-system/styles.css"
import { rootErrorHandlers, startMonitoring } from "@workspace/monitoring"
import { Providers } from "@/app/providers"
import { router } from "@/app/router"

void startMonitoring("simulator-ui")

createRoot(document.getElementById("root")!, rootErrorHandlers).render(
    <StrictMode>
        <Providers>
            <RouterProvider router={router} />
        </Providers>
    </StrictMode>
)
