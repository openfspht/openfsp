import type { ScenarioVerdict } from "@workspace/types"

import type { ValidatorCopy } from "./en"

export const fr: ValidatorCopy = {
    product: "Validateur",
    nav: { run: "Nouvelle exécution" },
    unreachable: "Impossible de joindre le validateur. Vérifiez votre connexion, puis réessayez.",
    home: {
        title: "Validateur de conformité",
        description: (concurrency?: number, retentionDays?: number) =>
            `Évaluez votre implémentation avec la suite de conformité OpenFSP. ${concurrency ?? "Plusieurs"} validations peuvent s'exécuter simultanément.${retentionDays === undefined ? "" : ` Les rapports sont conservés pendant ${retentionDays} jours.`}`,
        target: "Cible",
        targets: {
            server: "Serveur",
        },
        targetHints: {
            server: "La suite agit comme un client et appelle l'API OpenFSP du serveur soumis aux tests.",
        },
    },
    prepare: {
        webhookUrl: "Récepteur de webhooks",
        webhookHint:
            "Avant de lancer l'exécution, enregistrez cette URL sur le serveur comme point d'accès webhook pour tous les types d'événements de paiement. Les scénarios webhooks.emit examinent les événements reçus. Lors du dernier scénario, le récepteur répond 410 Gone ; le serveur peut alors désactiver ce point d'accès.",
        title: "Récepteur de webhooks",
        description:
            "Préparez une exécution pour obtenir, avant de la lancer, le récepteur de webhooks à enregistrer sur votre serveur. Un serveur qui prépare lui-même ses exécutions enregistre le récepteur sans intervention.",
        action: "Préparer une exécution",
        preparing: "Préparation",
        again: "Préparer une nouvelle exécution",
        startBefore: (time: string) =>
            `Lancez l'exécution avant ${time}. Passé ce délai, préparez une nouvelle exécution et utilisez la nouvelle URL.`,
        expired:
            "La préparation de cette exécution a expiré. Préparez une nouvelle exécution et utilisez la nouvelle URL.",
    },
    form: {
        title: "Valider un serveur",
        baseUrl: "URL du serveur",
        baseUrlHint:
            "La suite demande au serveur ses identifiants de test. Un serveur qui n'en fournit pas nécessite une clé de test.",
        baseUrlPlaceholder: "https://api.example.com",
        advanced: "Saisie manuelle",
        manualIntro:
            "À remplir seulement si votre serveur n'annonce pas de conformance_endpoint. Un serveur qui l'annonce prépare lui-même ses exécutions et fournit tous ces éléments (conformité 4.8).",
        needKey:
            "Ce serveur ne fournit pas d'identifiants de test. Saisissez une clé de test pour lancer la suite.",
        needCode: "Ce serveur demande un code d'accès de conformité. Demandez-le à son exploitant.",
        accessCode: "Code d'accès de conformité (facultatif)",
        accessCodeHint:
            "Uniquement pour un serveur qui restreint la préparation de ses exécutions de conformité.",
        apiKey: "Clé d'API de test (facultative)",
        apiKeyHint: (prefix: string) =>
            `Utilisez une clé commençant par ${prefix}. Les clés de production sont refusées.`,
        controlEndpoint: "Interface de contrôle (avec une clé d'API de test)",
        controlEndpointHint:
            "Le control_endpoint du serveur, sur l'origine de son URL. La suite y choisit l'issue des paiements ; il est requis avec une clé que vous fournissez.",
        restrictedKey: "Clé sans statements:read (facultative)",
        restrictedKeyHint:
            "Fournissez une seconde clé de test dépourvue de la portée statements:read. Sans cette clé, le scénario AUTH-6.4 reste assisté et le niveau Core ne peut pas être validé.",
        input: (name: string) => `Entrée ${name}`,
        inputHints: {
            "expired-key":
                "Une clé de test de la cible déjà expirée. Contre mock_openfsp, utilisez sa clé expirée publiée.",
            "revoked-key":
                "Une clé de test de la cible que vous avez révoquée, une fois écoulé le temps pendant lequel le serveur peut garder en cache une décision d'authentification. Contre mock_openfsp, utilisez sa clé révoquée publiée.",
            "bounded-provider":
                "L'identifiant d'un fournisseur pour lequel la configuration du serveur déclare min_amount, max_amount et amount_step.",
            "statements-key": "Une clé de test de la cible avec la portée statements:read.",
        },
        submit: "Valider",
        submitting: "Démarrage",
        busy: "La capacité maximale d'exécution est atteinte. Réessayez dans quelques minutes.",
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
        coreIncomplete: "Core incomplet : aucune conformité ne peut être revendiquée",
        coreIncompleteDetail:
            "Au moins un scénario Core n'a pas pu être vérifié. Rien n'a échoué, mais cette attestation ne constitue pas une déclaration de conformité.",
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
        badge: "Partager",
        shareLink: "Lien de partage",
        badgeHint:
            "Le lien de partage et le badge mènent à cette page. Le badge affiche le verdict Core et la version du protocole.",
        markdown: "Markdown",
        html: "HTML",
        validity: "Validité",
        validityStatement:
            "Cette attestation s'applique à la configuration testée et à la date de l'exécution. Elle n'est plus applicable si l'implémentation ou la version du protocole change.",
    },
    profiles: {
        title: "Profils",
        description:
            "Un profil est validé lorsque tous ses scénarios réussissent. Sélectionnez-en un pour afficher ses scénarios.",
    },
    tally: {
        met: (met: number, total: number) =>
            met === 1
                ? `1 scénario Core réussi sur ${total}`
                : `${met} scénarios Core réussis sur ${total}`,
        shortfall: (verdict: ScenarioVerdict, count: number) => {
            const word = {
                FAIL: "échoué",
                INCONCLUSIVE: "non concluant",
                ASSISTED: "assisté",
                PASS: "réussi",
                NOT_APPLICABLE: "non applicable",
            }[verdict]
            return `${count} ${count > 1 && verdict !== "NOT_APPLICABLE" ? `${word}s` : word}`
        },
    },
    filters: {
        title: "Scénarios",
        all: "Tous",
        reset: "Tout afficher",
        none: "Aucun scénario ne correspond à ce filtre.",
        verdicts: "Verdicts",
        passed: (passed: number, total: number) => `${passed}/${total} réussis`,
        count: (shown: number, total: number) =>
            shown === total ? `${total}` : `${shown} sur ${total}`,
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
