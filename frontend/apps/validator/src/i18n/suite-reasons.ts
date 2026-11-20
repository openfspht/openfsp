type Reasons = Record<string, (subject: string) => string>

const PATTERNS: Array<{ code: string; english: RegExp }> = [
    { code: "provider-not-announced", english: /^the target does not announce the provider (.+)$/ },
    { code: "capability-not-announced", english: /^no provider of the target announces (.+)$/ },
    { code: "capability-excluded", english: /^no provider of the target is (.+)$/ },
]

const PROFILE = /^no scenario of the profile applies to the target: (.+)$/

const FRENCH: Reasons & { profile: (reasons: string) => string } = {
    "provider-not-announced": (provider) => `la cible n’annonce pas le fournisseur ${provider}`,
    "capability-not-announced": (names) =>
        `aucun fournisseur de la cible n’annonce ${names.replaceAll(" and ", " et ")}`,
    "capability-excluded": (conditions) =>
        `aucun fournisseur de la cible n’est ${conditions
            .split(" and ")
            .map((part) => part.replace(/^announcing /, "annonçant ").replace(/^without /, "sans "))
            .join(" et ")}`,
    profile: (reasons) => `aucun scénario du profil ne s’applique à la cible : ${reasons}`,
}

export function translateReason(reason: string): string {
    const profile = PROFILE.exec(reason)
    if (profile) {
        return FRENCH.profile(profile[1].split("; ").map(translateReason).join(" ; "))
    }
    for (const { code, english } of PATTERNS) {
        const match = english.exec(reason)
        if (match) {
            return FRENCH[code](match[1])
        }
    }
    return reason
}
