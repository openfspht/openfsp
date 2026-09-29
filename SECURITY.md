# Politique de sécurité

OpenFSP est une infrastructure de paiement. Un défaut ici peut coûter de l'argent à
quelqu'un, exposer les identifiants d'opérateur d'un marchand, ou permettre de rejouer une
transaction. Nous traitons les signalements de sécurité en conséquence.

## Signaler une vulnérabilité

**N'ouvrez ni ticket, ni pull request, ni discussion publique pour un problème de
sécurité.**

Signalez-le en privé, par l'une des deux voies :

- **Signalement privé de vulnérabilité GitHub** : le bouton « Report a vulnerability » sous
  l'onglet Security du dépôt concerné. À préférer, parce qu'il garde le signalement, la
  discussion et l'avis de sécurité au même endroit.
- **Courriel** : security@karakosystems.com.

Indiquez, dans la mesure où vous pouvez l'établir : le composant et la version concernés,
ce qu'un attaquant peut obtenir, les étapes pour reproduire, et toute preuve de concept. Un
signalement que nous pouvons reproduire en vaut plusieurs que nous ne pouvons pas.

Vous pouvez écrire en français, en créole haïtien ou en anglais.

## Ce à quoi nous nous engageons

| Étape | Délai visé |
|---|---|
| Accusé de réception | 72 heures |
| Première évaluation et gravité | 7 jours |
| Correctif pour une gravité critique | 14 jours |
| Correctif pour une gravité élevée | 30 jours |
| Correctif pour une gravité modérée ou faible | prochaine version prévue |

Si un délai doit être dépassé, nous vous le dirons avant qu'il ne le soit, avec la raison.

Ces délais sont fixés par un seul éditeur ([GOVERNANCE §1](GOVERNANCE.md#1-porteur)) : ce sont des engagements de
moyens, pas des garanties contractuelles. Ils seront révisés, publiquement, si la capacité
du projet change.

## Divulgation

Nous pratiquons la divulgation coordonnée.

- Nous travaillons avec vous sur un correctif et vous créditons dans l'avis de sécurité,
  sauf si vous préférez le contraire.
- Nous publions un GitHub Security Advisory, avec un CVE lorsqu'il y a lieu.
- Nous vous demandons de retenir la divulgation publique jusqu'à la publication d'un
  correctif, ou pendant **90 jours** à compter de votre signalement, selon ce qui vient en
  premier. Passé 90 jours, vous êtes libre de publier quel que soit notre avancement, et
  nous n'y ferons pas objection : un délai que l'éditeur peut prolonger indéfiniment n'est
  pas un délai.
- Si une vulnérabilité fait l'objet d'une exploitation active, nous divulguerons et
  publierons aussi vite que possible plutôt que d'attendre.

Conformément à [GOVERNANCE.md §6](GOVERNANCE.md#6-gouvernance-de-la-sécurité), un correctif
de sécurité peut être publié avant le processus ADR habituel ; l'ADR qui le documente est
publiée en même temps que le correctif.

## Périmètre

**Dans le périmètre** : tous les dépôts publiés sous le projet OpenFSP, à savoir la
spécification elle-même (un défaut au niveau du protocole est une vulnérabilité, pas une
simple opinion de conception), le serveur passerelle, le serveur simulé, les SDK et la
suite de conformité.

**Hors périmètre**

- Les vulnérabilités dans les systèmes propres à un opérateur de paiement. Signalez-les à
  l'opérateur. Si le défaut est dans la façon dont *notre adaptateur* traite cet opérateur,
  il est dans le périmètre.
- La mauvaise configuration d'un déploiement que vous n'exploitez pas. Signalez-la à qui le
  fait tourner.
- Les résultats de scanners automatisés sans impact démontré.
- L'ingénierie sociale, les attaques physiques, ou le déni de service par volume de trafic.

## Sécurité du déploiement

La passerelle OpenFSP est **auto-hébergée** : l'exploitant détient les identifiants
d'opérateur, et le projet n'exploite aucun service hébergé ni ne manipule de fonds. C'est une
frontière architecturale délibérée, voir la [spécification
d'architecture](https://github.com/openfspht/adrs/blob/main/spec/architecture.md#déploiement-et-confiance).

Cela veut dire aussi que la sécurité d'un déploiement donné est partagée : nous sommes
responsables de la solidité du logiciel, et l'exploitant est responsable de la façon dont
il le fait tourner. Un guide de durcissement à l'usage des exploitants sera livré avec la
passerelle.

## Pas de programme de prime

Nous n'offrons pas de récompense financière. Nous sommes un projet jeune, sans les moyens
de tenir honnêtement un programme de primes, et nous préférons le dire plutôt qu'en
annoncer un que nous ne pourrions pas payer. Nous créditons les chercheurs publiquement et
avec gratitude.
