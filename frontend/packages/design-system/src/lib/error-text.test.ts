import { describe, expect, test } from "bun:test"
import { HttpError, NetworkError, UnauthenticatedError } from "@workspace/http"

import { en } from "../copy/en"
import { fr } from "../copy/fr"
import { errorText } from "./error-text"

describe("error text", () => {
    test("an answer without a problem is described in the reader's language", () => {
        const error = new HttpError("GET /gateway/admin/keys", 502)
        expect(errorText(error, en.action)).toBe("The server answered 502.")
        expect(errorText(error, fr.action)).toBe("Le serveur a répondu 502.")
    })

    test("an unreachable server and an expired session are translated", () => {
        const network = new NetworkError(
            "GET /gateway/admin/keys",
            new TypeError("Failed to fetch")
        )
        expect(errorText(network, fr.action)).toBe("Le serveur est injoignable.")
        const session = new UnauthenticatedError("GET /gateway/admin/keys")
        expect(errorText(session, fr.action)).toBe("La session a expiré. Reconnectez-vous.")
    })

    test("a problem keeps the reason given by the server", () => {
        const error = new HttpError("POST /gateway/admin/keys", 422, {
            errors: [{ field: "scopes", detail: "must not be empty" }],
        })
        expect(errorText(error, fr.action)).toBe("scopes: must not be empty")
    })

    test("any other error keeps its message", () => {
        expect(errorText(new Error("Invalid attestation"), en.action)).toBe("Invalid attestation")
    })
})
