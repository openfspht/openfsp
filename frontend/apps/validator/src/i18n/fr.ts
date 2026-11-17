import type { ValidatorCopy } from "./en"

export const fr: ValidatorCopy = {
    product: "Validateur",
    nav: { run: "Nouvelle exécution" },
    unreachable: "Impossible de joindre le validateur. Vérifiez votre connexion, puis réessayez.",
    home: {
        title: "Validateur de conformité",
        description: (concurrency?: number) =>
            `Évaluez votre implémentation avec la suite de conformité OpenFSP. Chaque exécution dispose d'un espace de simulation isolé : ${concurrency ?? "plusieurs"} utilisateurs peuvent lancer une validation simultanément, sans accès aux paiements des autres exécutions. Les rapports sont conservés pendant 90 jours.`,
        target: "Cible",
        targets: {
            server: "Serveur",
        },
        targetHints: {
            server: "La suite agit comme un client et appelle l'API OpenFSP du serveur soumis aux tests.",
        },
    },
    prepare: {
        title: "Récepteur de webhooks et espace de simulation",
        description:
            "Préparez une exécution pour obtenir, avant de la lancer, le récepteur de webhooks à enregistrer sur votre serveur et les adresses des serveurs de référence de son espace de simulation. Un serveur qui prépare lui-même ses exécutions enregistre le récepteur sans intervention.",
        action: "Préparer une exécution",
        preparing: "Préparation",
        again: "Préparer une nouvelle exécution",
        startBefore: (time: string) =>
            `Lancez l'exécution avant ${time}. Passé ce délai, préparez une nouvelle exécution et utilisez les nouvelles URL.`,
        expired:
            "La préparation de cette exécution a expiré. Préparez une nouvelle exécution et utilisez les nouvelles URL.",
    },
    setup: {
        title: "Espace de simulation de l'exécution",
        description:
            "Utilisez l'URL de base et la clé d'API de mock_openfsp pour définir la cible. Les paiements et les scénarios de cet espace sont isolés des autres exécutions. Chaque nouvelle exécution dispose de ses propres URL.",
        space: "Espace du simulateur",
        environment: "Environnement",
        keyPrefix: "Préfixe des clés",
        provider: "Service de paiement",
        baseUrl: "URL de base",
        credentials: "Identifiants",
        webhookUrl: "Récepteur de webhooks",
        webhookHint:
            "Avant de lancer l'exécution, enregistrez cette URL sur le serveur comme point d'accès webhook pour tous les types d'événements de paiement. Les scénarios webhooks.emit examinent les événements reçus. Lors du dernier scénario, le récepteur répond 410 Gone ; le serveur peut alors désactiver ce point d'accès.",
    },
    form: {
        title: "Valider un serveur",
        baseUrl: "URL du serveur",
        baseUrlHint:
            "La suite demande au serveur ses identifiants de test. Un serveur qui n'en fournit pas nécessite une clé de test.",
        baseUrlPlaceholder: "https://api.example.com",
        advanced: "Identifiants et options",
        reference: "Essayer le serveur de référence",
        needKey:
            "Ce serveur ne fournit pas d'identifiants de test. Saisissez une clé de test pour lancer la suite.",
        needCode: "Ce serveur demande un code d'accès de conformité. Demandez-le à son exploitant.",
        accessCode: "Code d'accès de conformité (facultatif)",
        accessCodeHint:
            "Uniquement pour un serveur qui restreint la préparation de ses exécutions de conformité.",
        apiKey: "Clé d'API de test (facultative)",
        apiKeyHint: (prefix: string) =>
            `Utilisez une clé commençant par ${prefix}. Les clés de production sont refusées.`,
        restrictedKey: "Clé sans statements:read (facultative)",
        restrictedKeyHint:
            "Fournissez une seconde clé de test dépourvue de la portée statements:read. Sans cette clé, le scénario AUTH-6.4 reste assisté et le niveau Core ne peut pas être validé.",
        input: (name: string) => `Entrée ${name}`,
        submit: "Valider",
        submitting: "Démarrage",
        busy: "La capacité maximale d'exécution est atteinte. Réessayez dans quelques minutes. L'espace de simulation de votre exécution préparée est conservé.",
        refused: "L'exécution a été refusée. Vérifiez les paramètres fournis avant de réessayer.",
    },
    run: {
        title: "Exécution",
        connecting: "Connexion en cours",
        progress: (done: number, total: number) => `${done} scénarios sur ${total}`,
        running: "En cours",
        pending: "En attente",
        report: "Consulter le rapport",
        lost: "Le flux d'événements a été interrompu. Consultez le rapport lorsqu'il sera disponible à la fin de l'exécution.",
        failed: "Exécution interrompue",
    },
    report: {
        title: "Rapport",
        download: "Télécharger le JSON",
        notFound: "Aucun rapport ne correspond à cette exécution. Vérifiez son identifiant.",
        loading: "Chargement du rapport",
        date: "Date",
        duration: (ms: number) => `${Math.round(ms / 1000)} s`,
        target: "Cible",
        protocol: "Protocole de la cible",
        unknownProtocol: "inconnu",
        suite: "Suite de conformité",
    },
    attestation: {
        title: "Attestation de conformité",
        issue: "Émettre l'attestation",
        issuing: "Émission",
        open: "Consulter l'attestation",
        loading: "Chargement de l'attestation",
        notFound:
            "Aucune attestation ne correspond à cet identifiant. Vérifiez l'identifiant utilisé.",
        statement:
            "Cette attestation consigne les résultats d'une exécution de la suite de conformité OpenFSP, y compris les échecs. Elle porte sur les résultats observés et ne constitue pas une certification de l'implémentation.",
        coreFailed: "Core non validé : implémentation non conforme dans la configuration testée",
        coreFailedDetail:
            "Cette attestation consigne un échec. Elle ne constitue pas une déclaration de conformité et ne doit pas être présentée comme telle.",
        corePassed: "Core validé",
        corePassedDetail:
            "Ce résultat s'applique uniquement à la configuration testée et à la date de l'exécution. Indiquez la version de l'implémentation testée.",
        verification: "Vérification",
        verifying: "Vérification en cours",
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
        keyStatus: (status: string) => ({ active: "active", retired: "retirée" })[status] ?? status,
        offline: "Vérifiez hors ligne avec la clé publiée sur",
        details: "Détails",
        issuer: "Émetteur",
        issuedAt: "Émise le",
        id: "Attestation",
        runDate: "Date de l'exécution",
        target: "Cible",
        baseUrl: "URL de base",
        protocol: "Version du protocole annoncée",
        suite: "Suite de conformité",
        report: "Rapport",
        digest: "Empreinte du rapport",
        keyId: "Identifiant de la clé",
        configuration: "Configuration",
        downloadPdf: "Télécharger le PDF",
        downloadJson: "Télécharger le JSON",
        openReport: "Consulter le rapport",
        badge: "Badge",
        badgeHint:
            "Le badge affiche le verdict Core et la version du protocole. Il renvoie vers cette page.",
        markdown: "Markdown",
        html: "HTML",
        validity: "Validité",
        validityStatement:
            "Cette attestation s'applique à la configuration testée et à la date de l'exécution. Elle n'est plus applicable si l'implémentation ou la version du protocole change.",
    },
    profiles: {
        title: "Profils et scénarios",
        description:
            "Un profil est validé lorsque tous ses scénarios réussissent. Si le serveur n'annonce pas la capacité correspondante, le profil est non applicable et sa conformité ne peut pas être revendiquée.",
    },
    summary: {
        core: "Core",
        profiles: "Profils",
        noProfiles: "Aucun scénario associé à un profil n'a été exécuté.",
        claim: "Déclaration de conformité",
        protocol: (version: string) => `protocole ${version}`,
        noClaim: "Aucune conformité ne peut être revendiquée : le niveau Core n'a pas été validé.",
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
        action: "Action requise",
        reason: "Motif",
        details: "Détails",
        empty: "Aucun scénario pour cette cible.",
        areas: {
            authentication: "Authentification",
            capabilities: "Capacités",
            errors: "Erreurs",
            failures: "Résultats des paiements",
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
