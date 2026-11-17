import type { DesignSystemCopy } from "./en"

export const fr: DesignSystemCopy = {
    locale: "fr-FR",
    status: {
        pending: "en attente",
        succeeded: "réussi",
        failed: "échoué",
        expired: "expiré",
        canceled: "annulé",
    },
    clipboard: { copy: "Copier", copied: "Copié" },
    modal: { cancel: "Annuler" },
    action: { failed: "L'action a échoué. Réessayez." },
    pagination: {
        label: "Pages",
        previous: "Page précédente",
        next: "Page suivante",
        pageOf: (page: number, total: number) => `Page ${page} sur ${total}`,
        results: (count: number) => (count === 1 ? "1 résultat" : `${count} résultats`),
    },
    filters: {
        all: "Tous",
        search: "Rechercher",
        status: "Statut",
        provider: "Service de paiement",
        kind: "Type",
    },
    export: { label: "Exporter", excel: "Excel", pdf: "PDF" },
    exchange: {
        status: "Statut HTTP",
        received: "Reçu",
        from: "Origine",
        duration: (ms: number) => `${ms} ms`,
        request: "Requête",
        response: "Réponse",
        noBody: "Aucun corps de message.",
    },
    language: {
        label: "Langue",
        en: "EN",
        fr: "FR",
        switchTo: { en: "Switch to English", fr: "Passer en français" },
    },
    header: {
        environment: (environment: string) => `environnement ${environment}`,
        signOut: "Se déconnecter",
        dark: "Mode sombre",
        light: "Mode clair",
    },
}
