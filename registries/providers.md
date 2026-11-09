# Registre des identifiants d'opérateurs

Ce registre attribue les identifiants `provider` qu'utilise l'[API
3.2](https://github.com/openfspht/adrs/blob/main/spec/api-paiements.md) et qu'annonce la
[découverte de capacités 3.2](https://github.com/openfspht/adrs/blob/main/spec/capacites.md).

Les mots-clés en capitales ont le sens défini dans
[spec/README.md](https://github.com/openfspht/adrs/blob/main/spec/README.md#mots-clés).

Une passerelle DOIT utiliser l'identifiant enregistré pour tout opérateur listé ici. Une
graphie locale rend les clients non portables d'un déploiement à l'autre.

## Identifiants enregistrés

| Identifiant | Service | Exploitant | Pays | Remplacé par |
|---|---|---|---|---|
| `moncash` | MonCash | Digicel | Haïti | |
| `natcash` | NatCash | Natcom | Haïti | |

## Réservés

| Identifiant | Motif |
|---|---|
| `openfsp` | Réservé au projet. |
| `mock_*` | Préfixe réservé aux opérateurs simulés, voir [conformité 9.3.4](https://github.com/openfspht/adrs/blob/main/spec/conformite.md). Aucun opérateur réel ne reçoit d'identifiant dans cet espace. |
| `test`, `example`, `invalid` | Réservés à la documentation et aux exemples ; aucun déploiement réel ne peut les revendiquer. |

## Enregistrer un identifiant

Ouvrez une pull request qui ajoute une ligne ; je la relis sans ADR, car un identifiant
est une étiquette sans comportement normatif propre (voir [découverte de capacités
2.7](https://github.com/openfspht/adrs/blob/main/spec/capacites.md)).

Un identifiant DOIT correspondre à `^[a-z0-9_]{1,32}$`, DOIT nommer le service de paiement
tel que ses utilisateurs le connaissent plutôt que sa société exploitante, et NE DOIT PAS
constituer une revendication de marque. Lister un opérateur ici n'implique aucune relation
avec cet opérateur, ni aucun soutien dans un sens ou dans l'autre.

Les identifiants sont permanents. Un opérateur qui change de nom reçoit un nouvel
identifiant, et l'ancienne ligne est conservée, avec le nouvel identifiant dans la colonne
*Remplacé par*, parce
que les déploiements et les enregistrements stockés continuent de porter l'ancienne valeur.
