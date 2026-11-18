import { HttpError, NetworkError, UnauthenticatedError } from "@workspace/http"

import type { DesignSystemCopy } from "../copy/en"

export function errorText(error: Error, copy: DesignSystemCopy["action"]): string {
    if (error instanceof NetworkError) {
        return copy.unreachable
    }
    if (error instanceof UnauthenticatedError) {
        return copy.unauthenticated
    }
    if (error instanceof HttpError) {
        return error.reason ?? copy.answered(error.status)
    }
    return error.message
}
