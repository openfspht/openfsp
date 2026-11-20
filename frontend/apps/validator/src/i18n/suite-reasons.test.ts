import { describe, expect, it } from "bun:test"

import { translateReason } from "./suite-reasons"

describe("translateReason", () => {
    it("translates each reason the engine writes", () => {
        expect(translateReason("the target does not announce the provider mock_x")).toBe(
            "la cible n’annonce pas l’opérateur mock_x"
        )
        expect(
            translateReason("no provider of the target announces payments.lookup and payments.fee")
        ).toBe("aucun opérateur de la cible n’annonce payments.lookup et payments.fee")
        expect(translateReason("no provider of the target is without payments.lookup")).toBe(
            "aucun opérateur de la cible n’est sans payments.lookup"
        )
    })

    it("translates the reasons a profile gathers and keeps an unknown reason as is", () => {
        expect(
            translateReason(
                "no scenario of the profile applies to the target: no provider of the target announces payments.fee; something new"
            )
        ).toBe(
            "aucun scénario du profil ne s’applique à la cible : aucun opérateur de la cible n’annonce payments.fee ; something new"
        )
    })
})
