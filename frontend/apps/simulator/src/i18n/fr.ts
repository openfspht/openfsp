import type { Messages } from "./en"

export const fr: Messages = {
    product: "Simulateur",
    nav: {
        activity: "Activité",
        requests: "Requêtes",
    },
    operators: {
        mock_openfsp: "OpenFSP (mock)",
        mock_moncash: "MonCash (mock)",
        mock_natcash: "NatCash (mock)",
    },
    unreachable: "Impossible de joindre le simulateur. Vérifiez votre connexion, puis réessayez.",
    activity: {
        title: "Activité",
        description:
            "Consultez les paiements des services simulés, et définissez les scénarios des prochains paiements.",
        search: "Paiement, commande, téléphone ou montant",
    },
    requests: {
        title: "Requêtes",
        description:
            "Journal des appels reçus par les services de paiement simulés. Les identifiants d'authentification sont masqués.",
        search: "Chemin ou corps",
    },
    operator: "Service de paiement",
    allOperators: "Tous les services",
    payments: {
        payment: "Paiement",
        scenario: "Scénario",
        amount: "Montant",
        state: "Statut",
        exchanges: (callbacks: number, lookups: number) =>
            `${callbacks} callback${callbacks > 1 ? "s" : ""}, ${lookups} consultation${lookups > 1 ? "s" : ""}`,
        empty: "Le simulateur n'a encore reçu aucun paiement.",
        reported: (amount: string) => `montant déclaré : ${amount}`,
        sendCallback: "Envoyer le callback",
        settleDescription:
            "Définissez le résultat que le service simulé renverra pour ce paiement.",
        callbackDescription: "Renvoyez le callback du service simulé pour ce paiement.",
        noCallback: "Aucune URL de callback n'est associée à ce paiement.",
    },
    resolution: {
        succeed: "Simuler une réussite",
        decline: "Simuler un refus",
        expire: "Simuler une expiration",
    },
    journal: {
        received: "Reçue",
        request: "Requête",
        empty: "Aucune requête pour l'instant.",
    },
    scenarios: {
        title: "Scénarios",
        description:
            "Configurez le comportement des prochains paiements de chaque service simulé. En mode automatique, les scénarios sont appliqués successivement à chaque nouvelle commande, de manière cyclique.",
        next: "Prochains paiements",
        automatic: "Automatique",
        automaticLabel: (operator: string) => `Activer la rotation des scénarios pour ${operator}`,
        rotating: (count: number) => `Rotation sur ${count} scénarios`,
        refused:
            "La modification n'a pas été appliquée. Vérifiez les paramètres du scénario, puis réessayez.",
    },
    reset: {
        label: "Réinitialiser",
        title: "Réinitialiser le simulateur ?",
        description:
            "Cette action efface les paiements, les scénarios et les requêtes pour tous les utilisateurs de ce simulateur.",
        confirm: "Réinitialiser",
    },
}
