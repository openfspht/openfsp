import type { Messages } from "./en"

export const fr: Messages = {
  product: "simulateur",
  nav: {
    activity: "Activité",
    requests: "Requêtes",
  },
  operators: {
    mock_openfsp: "OpenFSP (mock)",
    mock_moncash: "MonCash (mock)",
    mock_natcash: "NatCash (mock)",
  },
  unreachable: "Le simulateur est injoignable.",
  activity: {
    title: "Activité",
    description:
      "Les paiements que détiennent les opérateurs simulés. Choisissez le comportement des suivants et clôturez les journées à rapprocher.",
    search: "Paiement, commande, téléphone ou montant",
  },
  requests: {
    title: "Requêtes",
    description:
      "Chaque appel de la passerelle aux opérateurs simulés, identifiants masqués.",
    search: "Chemin ou corps",
  },
  operator: "Opérateur",
  allOperators: "Tous les opérateurs",
  payments: {
    payment: "Paiement",
    scenario: "Scénario",
    amount: "Montant",
    state: "Statut",
    exchanges: (callbacks: number, lookups: number) =>
      `${callbacks} callback${callbacks > 1 ? "s" : ""}, ${lookups} consultation${lookups > 1 ? "s" : ""}`,
    empty: "Aucun paiement n'a encore atteint le simulateur.",
    reported: (amount: string) => `déclaré ${amount}`,
    sendCallback: "Envoyer le callback",
    settleDescription: "Choisissez ce que l'opérateur répond pour ce paiement.",
    callbackDescription:
      "Renvoyez le callback de l'opérateur pour ce paiement.",
    noCallback: "Ce paiement n'a pas d'URL de callback.",
  },
  resolution: { succeed: "Réussir", decline: "Refuser", expire: "Expirer" },
  journal: {
    received: "Reçue",
    request: "Requête",
    empty: "Aucune requête pour l'instant.",
    status: "Statut",
    duration: (ms: number) => `${ms} ms`,
  },
  scenarios: {
    title: "Scénarios",
    description:
      "Le comportement des prochains paiements de chaque opérateur. Auto donne à chaque nouvelle commande le scénario suivant, à tour de rôle.",
    next: "Prochains paiements",
    automatic: "Auto",
    automaticLabel: (operator: string) =>
      `Faire tourner les scénarios de ${operator}`,
    rotating: (count: number) => `Rotation sur ${count} scénarios`,
    refused: "Le simulateur n'a pas appliqué le changement.",
  },
  statements: {
    title: "Clôturer une journée",
    description:
      "Marquez une journée comme complète pour que la passerelle la rapproche. Les journées suivent l'heure d'Haïti.",
    day: "Journée",
    close: "Clôturer la journée",
    closed: "Déjà clôturée",
    future: "Cette journée n'a pas commencé.",
    closedDays: (days: string) => `Clôturées : ${days}`,
    none: "Aucune journée clôturée.",
  },
  reset: {
    label: "Réinitialiser",
    title: "Réinitialiser le simulateur ?",
    description:
      "Les paiements, scénarios, requêtes et journées clôturées sont effacés pour tous les utilisateurs de ce simulateur.",
    confirm: "Réinitialiser",
  },
}
