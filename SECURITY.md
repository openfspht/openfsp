# Politique de sécurité

## Signaler une vulnérabilité

**N'ouvrez ni ticket, ni pull request, ni discussion publique pour une
vulnérabilité.**

Signalez-le en privé :

- **Signalement privé de vulnérabilité GitHub** (voie préférée) : bouton « Report a
  vulnerability » sous l'onglet Security du dépôt concerné.
- **Courriel** : security@karakosystems.com.

Indiquez si possible : le composant et la version concernés, ce qu'un attaquant peut
obtenir, les étapes pour reproduire, et toute preuve de concept.

Je lis le français, le créole haïtien et l'anglais.

## Délais de traitement

| Étape | Délai visé |
|---|---|
| Accusé de réception | 72 heures |
| Première évaluation et gravité | 7 jours |
| Correctif pour une gravité critique | 14 jours |
| Correctif pour une gravité élevée | 30 jours |
| Correctif pour une gravité modérée ou faible | prochaine version prévue |

Si un délai doit être dépassé, je vous en informe avant l'échéance, avec la raison.

Ces délais reposent sur moi seul, éditeur unique ([GOVERNANCE 1](GOVERNANCE.md#1-porteur)) :
ce sont des engagements de moyens, pas des garanties contractuelles. Je les réviserai
publiquement si la capacité du projet change.

## Divulgation

Divulgation coordonnée :

- Je travaille avec vous sur un correctif et vous crédite dans l'avis de sécurité,
  sauf refus de votre part.
- Je publie un GitHub Security Advisory, avec un CVE lorsqu'il y a lieu.
- Je vous demande de retenir la divulgation publique jusqu'à la publication d'un
  correctif, ou pendant **90 jours** à compter de votre signalement, selon ce qui vient en
  premier. Passé 90 jours, vous êtes libre de publier quel que soit l'avancement du correctif.
- En cas d'exploitation active, je divulgue et publie sans attendre l'échéance de
  90 jours.

Conformément à [GOVERNANCE.md 6](GOVERNANCE.md#6-gouvernance-de-la-sécurité), un correctif
de sécurité peut être publié avant le processus ADR habituel ; l'ADR qui le documente est
publiée en même temps que le correctif.

## Périmètre

**Dans le périmètre** : tous les dépôts publiés sous le projet OpenFSP, à savoir la
spécification (un défaut de sécurité au niveau du protocole est une vulnérabilité),
la passerelle, le serveur simulé, les SDK et la suite de conformité.

**Hors périmètre**

- Les vulnérabilités dans les systèmes propres à un opérateur de paiement : signalez-les à
  l'opérateur. Un défaut dans la façon dont *l'adaptateur OpenFSP* traite cet opérateur est dans
  le périmètre.
- La mauvaise configuration d'un déploiement que vous n'exploitez pas : signalez-la à son
  exploitant.
- Les résultats de scanners automatisés sans impact démontré.
- L'ingénierie sociale, les attaques physiques, ou le déni de service par volume de trafic.

## Sécurité du déploiement

La passerelle OpenFSP est **auto-hébergée** : l'exploitant détient les secrets
d'opérateur, et le projet n'exploite aucun service hébergé ni ne manipule de fonds. Voir la
[spécification
d'architecture](https://github.com/openfspht/adrs/blob/main/spec/architecture.md#déploiement-et-confiance).

Le projet répond des défauts du logiciel publié ; l'exploitant répond de sa configuration et
de son exploitation. Un guide de durcissement pour les exploitants sera livré avec la passerelle.

## Pas de programme de prime

Aucune récompense financière. Je crédite les chercheurs dans l'avis de sécurité, sauf refus de leur part.
