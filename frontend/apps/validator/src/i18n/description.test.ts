import { describe, expect, test } from "bun:test"

import { en } from "./en"
import { fr } from "./fr"

describe("home description", () => {
    test("states the retention the validator announces", () => {
        expect(en.home.description(4, 30)).toContain("retained for 30 days")
        expect(fr.home.description(4, 30)).toContain("conservés pendant 30 jours")
    })

    test("promises no retention the validator has not announced", () => {
        expect(en.home.description(4)).not.toContain("retained")
        expect(fr.home.description(4)).not.toContain("conservés")
    })
})
