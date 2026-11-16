import type { ValidatorCopy } from "./en"

export const fr: ValidatorCopy = {
  product: "validateur",
  nav: { run: "Nouvelle exécution" },
  unreachable: "Le validateur est injoignable.",
  home: {
    title: "Validateur de conformité",
    description: (concurrency?: number) =>
      `Exécute la suite de conformité OpenFSP contre votre implémentation. Chaque exécution a son propre espace dans le simulateur : ${concurrency ?? "plusieurs"} personnes peuvent valider en même temps sans voir les paiements des autres. Les rapports sont gardés 90 jours.`,
    target: "Cible",
    targets: {
      server: "Serveur",
    },
    targetHints: {
      server:
        "La suite joue le client et appelle l'API OpenFSP du serveur testé.",
    },
  },
  prepare: {
    title: "Serveur fictif de référence (facultatif)",
    description:
      "Pour valider mock_openfsp, le serveur fictif de référence, préparez une exécution : lancez-la avec l'URL de base et la clé de mock_openfsp de son espace ci-dessous, et la suite choisit les dénouements des paiements de cet espace. Tout autre serveur se passe de préparation.",
    action: "Préparer une exécution",
    preparing: "Préparation",
    again: "Préparer une nouvelle exécution",
    startBefore: (time: string) =>
      `Lancez avant ${time}. Ensuite, préparez une nouvelle exécution et prenez ses nouvelles URL.`,
    expired:
      "Cette exécution préparée a expiré. Préparez-en une nouvelle et prenez ses nouvelles URL.",
  },
  setup: {
    title: "Espace du serveur fictif pour cette exécution",
    description:
      "Prenez l'URL de base et la clé d'API de mock_openfsp comme cible. Aucune autre exécution ne voit les paiements ni les scénarios de cet espace. Chaque nouvelle exécution a de nouvelles URL.",
    space: "Espace du simulateur",
    environment: "Environnement",
    keyPrefix: "Préfixe des clés",
    provider: "Fournisseur",
    baseUrl: "URL de base",
    credentials: "Identifiants",
    webhookUrl: "Récepteur de webhooks",
    webhookHint:
      "Déclarez cette URL au serveur comme point d'accès de webhooks pour tous les types d'événement de paiement avant de lancer : les scénarios webhooks.emit lisent les événements qu'elle reçoit. Le dernier répond 410 Gone, après quoi le serveur peut désactiver le point d'accès.",
  },
  form: {
    title: "Exécution",
    titleAfterPreparation: "2. Lancer l'exécution",
    baseUrl: "URL de base",
    baseUrlPlaceholder: "https://api.example.com",
    apiKey: "Clé d'API de test",
    apiKeyHint: (prefix: string) =>
      `Commence par ${prefix}. Les clés live sont refusées.`,
    restrictedKey: "Clé sans statements:read (facultative)",
    restrictedKeyHint:
      "Une seconde clé de test sans la portée statements:read. Sans elle, AUTH-6.4 reste assisté et Core ne peut pas réussir.",
    webhookUrl: "Récepteur de webhooks de cette exécution",
    webhookHint:
      "Déclaré au serveur comme point d'accès pour tous les types d'événement de paiement, il permet d'exécuter les scénarios webhooks.emit.",
    input: (name: string) => `Entrée ${name}`,
    submit: "Lancer",
    submitting: "Démarrage",
    busy: "Toutes les places d'exécution sont prises. Réessayez dans quelques minutes : votre exécution préparée garde son espace dans le simulateur.",
    refused: "L'exécution a été refusée.",
  },
  run: {
    title: "Exécution",
    connecting: "Connexion.",
    progress: (done: number, total: number) => `${done} scénarios sur ${total}`,
    running: "En cours",
    pending: "En attente",
    report: "Ouvrir le rapport",
    lost: "Le flux d'événements a été interrompu. Le rapport apparaît à la fin de l'exécution.",
    failed: "L'exécution s'est arrêtée",
  },
  report: {
    title: "Rapport",
    download: "Télécharger le JSON",
    notFound: "Aucun rapport pour cette exécution.",
    loading: "Chargement du rapport.",
    date: "Date",
    duration: (ms: number) => `${Math.round(ms / 1000)} s`,
    target: "Cible",
    protocol: "Protocole de la cible",
    unknownProtocol: "inconnu",
    suite: "Suite",
  },
  attestation: {
    title: "Attestation de conformité",
    issue: "Émettre l'attestation",
    issuing: "Émission",
    open: "Ouvrir l'attestation",
    loading: "Chargement de l'attestation.",
    notFound: "Aucune attestation sous cet identifiant.",
    statement:
      "Cette attestation consigne les résultats d'une exécution de la suite de conformité OpenFSP, échecs compris. Elle atteste des résultats ; elle ne certifie pas une implémentation.",
    coreFailed: "Core n'a pas réussi : cette implémentation n'est pas conforme",
    coreFailedDetail:
      "Cette attestation consigne un échec. Ce n'est pas une revendication de conformité et elle ne doit pas être présentée comme telle.",
    corePassed: "Core a réussi",
    corePassedDetail:
      "Pour cette configuration et cette date seulement. Citez la version de l'implémentation testée.",
    verification: "Vérification",
    verifying: "Vérification.",
    verified: "Vérifiée",
    notVerified: "Non vérifiée",
    checks: {
      signature: ["Signature valide", "Signature invalide"],
      digest: [
        "L'empreinte correspond au rapport conservé",
        "L'empreinte ne correspond pas au rapport conservé",
      ],
      key: ["Clé publiée par ce validateur", "Clé inconnue"],
    },
    keyStatus: (status: string) =>
      ({ active: "active", retired: "retirée" })[status] ?? status,
    offline: "Vérifiez hors ligne avec la clé publiée sur",
    details: "Détails",
    issuer: "Émetteur",
    issuedAt: "Émise le",
    id: "Attestation",
    runDate: "Date de l'exécution",
    target: "Cible",
    baseUrl: "URL de base",
    protocol: "Protocole annoncé",
    suite: "Suite",
    report: "Rapport",
    digest: "Empreinte du rapport",
    keyId: "Identifiant de clé",
    configuration: "Configuration",
    downloadPdf: "Télécharger le PDF",
    downloadJson: "Télécharger le JSON",
    openReport: "Ouvrir le rapport",
    badge: "Badge",
    badgeHint:
      "Affiche le verdict Core et la version du protocole, avec un lien vers cette page.",
    markdown: "Markdown",
    html: "HTML",
    validity: "Validité",
    validityStatement:
      "Vaut pour cette configuration et cette date ; périmée quand l'implémentation ou la version du protocole change.",
  },
  profiles: {
    title: "Profils et leurs scénarios",
    description:
      "Un profil réussit quand chacun de ses scénarios réussit. Un profil dont le serveur n'annonce pas la capacité est sans objet et ne peut pas être revendiqué.",
  },
  summary: {
    core: "Core",
    profiles: "Profils",
    noProfiles: "Aucun scénario de profil n'a été exécuté.",
    claim: "Revendication",
    protocol: (version: string) => `protocole ${version}`,
    noClaim: "Aucune revendication de conformité : Core n'a pas réussi.",
    limits: "Limites de ce rapport",
  },
  results: {
    scenario: "Scénario",
    requirement: "Exigence",
    verdict: "Verdict",
    expected: "Attendu",
    observed: "Observé",
    step: "Étape",
    exchange: "Échange",
    action: "Action à mener",
    reason: "Motif",
    details: "Détails",
    empty: "Aucun scénario pour cette cible.",
    areas: {
      authentication: "Authentification",
      capabilities: "Capacités",
      errors: "Erreurs",
      failures: "Dénouements de paiement",
      idempotency: "Idempotence",
      lifecycle: "Cycle de vie",
      payments: "Paiements",
      statements: "Relevés",
      webhooks: "Webhooks",
    },
  },
  verdicts: {
    PASS: "réussi",
    FAIL: "échoué",
    NOT_APPLICABLE: "non applicable",
    INCONCLUSIVE: "non concluant",
    ASSISTED: "assisté",
  },
  levels: {
    PASS: "réussi",
    FAIL: "échoué",
    INCOMPLETE: "incomplet",
    NOT_APPLICABLE: "non applicable",
  },
}
