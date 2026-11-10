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
}
