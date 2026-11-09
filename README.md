<div align="center">

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="https://cdn.openfsp.org/logo/openfsp-logo-inverse.svg">
  <img src="https://cdn.openfsp.org/logo/openfsp-logo.svg" alt="OpenFSP" width="320">
</picture>

**Proposition de norme ouverte d'intégration des paiements en Haïti**

[![Licence](https://img.shields.io/badge/licence-Apache--2.0-blue)](LICENSE)
[![Statut](https://img.shields.io/badge/statut-sp%C3%A9cification%20en%20brouillon-orange)](https://github.com/openfspht/adrs)

</div>

OpenFSP est le projet de recherche que je propose pour mon master MBDS : un contrat ouvert
unique entre les applications marchandes et les opérateurs de paiement numérique haïtiens.
C'est d'abord un travail académique, que j'ouvrirai ensuite à la contribution.

## L'idée

Sans contrat commun, chacune des `L` applications marchandes intègre chacun des `O`
opérateurs : `O × L` intégrations. Avec OpenFSP, chaque application parle un seul protocole
et chaque opérateur est intégré une seule fois, dans la passerelle : `O + L`.

```mermaid
flowchart LR
    subgraph apps["L applications marchandes"]
        A1["PHP"]
        A2["TypeScript"]
        A3["Python"]
    end
    G["<b>Passerelle OpenFSP</b><br/><i>hébergée par le marchand</i>"]
    subgraph ops["O opérateurs"]
        P1["Opérateur 1"]
        P2["Opérateur 2"]
    end
    A1 --> G
    A2 --> G
    A3 --> G
    G --> P1
    G --> P2
```

## Composants

| Composant | Rôle |
|---|---|
| **Spécification** | Le contrat : modèle de données, cycle de vie, capacités, erreurs, idempotence, webhooks. |
| **Passerelle** | Serveur que le marchand héberge : protocole OpenFSP en entrée, API des opérateurs en sortie. |
| **Serveur simulé** | Reproduit les opérateurs, défaillances comprises, pour tester sans compte marchand. |
| **Bibliothèques clientes** | Clients minces en TypeScript, PHP et Python ; le protocole reste utilisable en HTTP seul. |
| **Suite de conformité** | Vérifie qu'une implémentation respecte la spécification ; un tiers peut la réexécuter. |

## Pour aller plus loin

Spécification et ADR : [openfspht/adrs](https://github.com/openfspht/adrs). Gouvernance :
[GOVERNANCE.md](GOVERNANCE.md). Licence : [Apache-2.0](LICENSE).

**Contribuer** : je reçois relectures et propositions par le
[processus ADR](https://github.com/openfspht/adrs), selon [CONTRIBUTING.md](CONTRIBUTING.md).
