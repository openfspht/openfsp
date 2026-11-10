import type { Messages } from "./en"

export const fr: Messages = {
  product: "simulateur",
  nav: {
    payments: "Paiements",
    requests: "Requêtes",
    scenarios: "Scénarios",
    statements: "Relevés",
  },
  operators: {
    mock_openfsp: "OpenFSP (mock)",
    mock_moncash: "MonCash (mock)",
    mock_natcash: "NatCash (mock)",
  },
  unreachable: "Le simulateur est injoignable.",
  payments: {
    title: "Paiements",
    description:
      "Ce que détiennent les opérateurs simulés, tels que la passerelle les a créés.",
    search: "Paiement, commande, téléphone ou montant",
  },
  requests: {
    title: "Requêtes",
    description:
      "Chaque appel de la passerelle aux opérateurs simulés, identifiants masqués.",
    search: "Chemin ou corps",
  },
  scenarios: {
    title: "Scénarios",
    description: "Le comportement des prochains paiements de chaque opérateur.",
  },
  statements: {
    title: "Relevés",
    description: "Clôturez une journée pour que la passerelle la rapproche.",
  },
}
