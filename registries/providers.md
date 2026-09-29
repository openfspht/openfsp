# Registre des identifiants d'opérateurs

Ce registre attribue les identifiants `provider` qu'utilise l'[API
§3.2](https://github.com/openfspht/adrs/blob/main/spec/api-paiements.md) et qu'annonce la
[découverte de capacités §3.2](https://github.com/openfspht/adrs/blob/main/spec/capacites.md).

Une passerelle DOIT utiliser l'identifiant enregistré pour tout opérateur listé ici.
Inventer une graphie locale pour un opérateur enregistré rend les clients non portables
d'un déploiement à l'autre, ce qui ruine l'objet même d'un protocole commun.

## Identifiants enregistrés

| Identifiant | Service | Exploitant | Pays |
|---|---|---|---|
| `moncash` | MonCash | Digicel | Haïti |
| `natcash` | NatCash | Natcom | Haïti |

## Réservés

| Identifiant | Motif |
|---|---|
| `openfsp` | Réservé au projet. |
| `mock_*` | Préfixe réservé aux fournisseurs de la simulation, voir le [serveur simulé §3.4](https://github.com/openfspht/adrs/blob/main/spec/serveur-simule.md). Aucun opérateur réel ne reçoit d'identifiant dans cet espace. |
| `test`, `example`, `invalid` | Réservés à la documentation et aux exemples, pour qu'aucun déploiement réel ne puisse les revendiquer. |

## Enregistrer un identifiant

Ouvrez une pull request qui ajoute une ligne. Aucune ADR n'est requise, pour la raison donnée
dans la [découverte de capacités
§2.7](https://github.com/openfspht/adrs/blob/main/spec/capacites.md) : un identifiant est une
étiquette, et ne porte aucun comportement normatif propre.

Un identifiant DOIT correspondre à `^[a-z0-9_]{1,32}$`, DOIT nommer le service de paiement
tel que ses utilisateurs le connaissent plutôt que sa société exploitante, et NE DOIT PAS
constituer une revendication de marque. Lister un opérateur ici n'implique aucune relation
avec cet opérateur, ni aucun soutien dans un sens ou dans l'autre.

Les identifiants sont permanents. Un opérateur qui change de nom reçoit un nouvel
identifiant, et l'ancienne ligne est marquée comme remplacée plutôt que supprimée, parce
que les déploiements et les enregistrements stockés continuent de porter l'ancienne valeur.
