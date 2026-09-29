# Gouvernance

Ce document énonce qui décide de quoi dans OpenFSP, comment les décisions sont consignées,
quelles garanties sont données à ceux qui adoptent la norme, et à quelles conditions la
gouvernance s'ouvre.

Il est écrit pour trois publics : les développeurs qui décident s'ils vont bâtir sur
OpenFSP, les opérateurs de paiement qui décident s'ils vont l'implémenter, et les
régulateurs qui décident s'ils vont s'y fier. Les trois ont besoin de la même chose :
savoir que la spécification ne peut pas changer arbitrairement, et qu'elle ne peut pas
leur être retirée.

## 1. Porteur

OpenFSP est édité et porté par **Karako Systems** (Haïti, https://karakosystems.com), qui
détient la gouvernance du projet, ses dépôts et son nom.

Karako Systems nomme l'**éditeur**, qui est le décideur technique du projet. L'éditeur est
responsable de la cohérence de la spécification, de l'acceptation des ADR et de la
publication de la suite de conformité.

**À ce jour, le projet a un seul éditeur et aucun autre mainteneur.** Nous le disons
clairement plutôt que de décrire un comité qui n'existe pas. La section 7 définit
exactement ce qui fera changer cela.

## 2. Intérêt déclaré

Karako Systems construit des produits commerciaux au-dessus d'OpenFSP. Quiconque évalue
cette spécification doit partir du principe que le porteur y a un intérêt commercial.

Plutôt que de nier cet intérêt, nous l'encadrons :

1. **Aucune voie privilégiée.** Les changements motivés par un produit commercial du
   porteur passent par le même processus ADR public que n'importe quel autre changement,
   sous la même relecture, dans les mêmes délais. Il n'existe pas de voie privée.
2. **Motivation déclarée.** Quand une ADR est poussée par un besoin d'un produit du
   porteur, sa section *Décision* le dit. Les relecteurs sont en droit de savoir de qui
   est le problème que l'on résout.
3. **Aucun verrouillage par la spécification.** La spécification ne doit pas contenir
   d'exigence dont le seul objet serait d'avantager une implémentation. Tout relecteur
   peut contester une disposition sur ce motif, et l'éditeur doit répondre publiquement à
   la contestation avant que l'ADR n'avance.
4. **Parité d'accès aux fonctionnalités.** Toute capacité qu'utilisent les produits du
   porteur fait partie de la spécification publique. Il n'existe aucune extension non
   documentée.

Ces quatre contraintes ne sortent pas de nulle part. Le droit haïtien impose la même forme aux
établissements réglementés : l'article 85 de la loi du 14 mai 2012 sur les banques et autres
institutions financières les oblige à agir « loyalement et équitablement au mieux des intérêts
de leurs clients et de l'intégrité du marché », et à « s'efforcer d'écarter les conflits
d'intérêt et, lorsque ces derniers ne peuvent être évités, à veiller à ce que leurs clients
soient traités équitablement ». La règle impose d'écarter le conflit là où c'est possible et
de le neutraliser là où ce ne l'est pas. C'est exactement ce que font les quatre contraintes
ci-dessus. Karako Systems n'est pas une banque et cet article ne la lie pas ; il est cité
parce que c'est la norme qu'un lecteur haïtien appliquera.

Si OpenFSP venait à être approuvé par un régulateur, ou à servir d'appui à celui-ci, ces
contraintes deviendraient la base qui justifie cette confiance. Elles ne sont pas
décoratives.

## 3. Engagement de licence

Les engagements suivants sont pris par Karako Systems envers tous ceux qui adoptent
OpenFSP, et sont destinés à être invoqués :

- **Tout ce qui est publié sous OpenFSP est sous licence Apache-2.0.** La spécification, le
  serveur passerelle, le serveur simulé, les SDK et la suite de conformité. Aucun composant
  n'est retenu sous une autre licence.
- **Aucun accord de licence de contributeur.** Les contributions sont acceptées sous le
  Developer Certificate of Origin (voir [CONTRIBUTING.md](CONTRIBUTING.md)). Les
  contributeurs conservent leur droit d'auteur, et aucun droit n'est cédé au porteur.
- **Le projet ne peut pas être refermé.** Comme les contributions ne sont cédées à aucun
  propriétaire unique, aucune décision future du porteur ne peut retirer rétroactivement la
  concession Apache-2.0 sur ce qui a été publié. C'est un choix structurel délibéré, pas une
  simple promesse.
- **Le fork est un recours légitime.** Si la gouvernance du porteur échoue, la licence
  permet à la communauté de poursuivre le travail. Cette porte de sortie est
  intentionnelle.

## 4. Comment les décisions sont prises

Deux voies, et c'est la frontière entre les deux qui compte.

### ADR requise

Une ADR (voir [`adrs`](https://github.com/openfspht/adrs)) est requise pour tout ce qui
change ce qu'une implémentation conforme doit faire :

- Tout changement du protocole sur le fil : ressources, champs, valeurs de statut, codes
  d'erreur.
- Toute nouvelle capacité, ou tout changement dans la façon dont les capacités sont
  annoncées.
- Tout changement des exigences de conformité ou du sens de la suite de conformité.
- Tout changement de versionnement, de dépréciation ou d'exigence de sécurité.
- La suppression ou la dépréciation de quoi que ce soit de déjà spécifié.
- Ce document, et le processus ADR lui-même.

### ADR non requise

Une pull request suffit pour :

- Le travail d'implémentation qui ne modifie pas le comportement spécifié : corrections de
  bogues, performance, remaniement, structure interne de la passerelle ou des SDK.
- Les nouveaux adaptateurs d'opérateurs, lorsque l'opérateur entre dans le modèle de
  capacités existant. S'il n'y entre pas, c'est une ADR.
- La documentation, les exemples, les tests, l'outillage, les traductions.
- Les corrections éditoriales d'une ADR acceptée qui n'en changent pas le sens.

Quand on ne sait pas quelle voie s'applique, ouvrez un ticket et posez la question. Se
tromper dans le sens de l'ADR coûte quelques jours ; se tromper dans l'autre sens livre un
changement cassant silencieux à des gens qui avaient fait confiance à la spécification.

### Résolution

L'éditeur décide, publiquement, dans le fil de l'ADR, avec des motifs. Le consensus est
recherché et il est généralement atteint ; ce n'est pas une exigence formelle tant que le
projet n'a qu'un seul éditeur. Prétendre le contraire serait une fiction, et tout l'objet
de ce document est que l'on puisse savoir exactement combien de processus se tient derrière
une décision.

## 5. Garanties de stabilité

Un implémenteur est en droit de compter sur les garanties suivantes.

- **La spécification est versionnée en versionnement sémantique.** Pour
  `MAJEUR.MINEUR.CORRECTIF` : un incrément MAJEUR peut casser les implémentations
  conformes ; un incrément MINEUR ajoute une capacité en restant rétrocompatible ; un
  incrément CORRECTIF clarifie la formulation sans changer le comportement.
- **La rétrocompatibilité n'est pas rompue sans une ADR acceptée.** Il n'y a pas
  d'exception pour l'urgence. Un problème urgent reçoit une ADR urgente.
- **Une dépréciation exige un préavis d'une version MAJEURE complète.** Ce qui est déprécié
  en `2.x` ne peut être retiré avant `3.0`, et doit être marqué comme déprécié dans la
  spécification, dans les réponses de la passerelle et dans les notes de version pendant
  toute cette durée.
- **Les errata sont tracés.** Quand une formulation acceptée se révèle fausse ou ambiguë, elle
  est corrigée sur place par un commit qui le dit, jamais en silence.
- **Exception avant 1.0.** Tant que la spécification n'a pas atteint `1.0.0`, des
  changements cassants peuvent survenir entre versions MINEURES. Nous le disons pour que
  personne ne bâtisse par accident une intégration de production sur une cible mouvante.
  Chaque changement cassant d'avant 1.0 exige malgré tout une ADR acceptée.

## 6. Gouvernance de la sécurité

Les signalements de vulnérabilité suivent [SECURITY.md](SECURITY.md) et sont traités en
privé jusqu'à ce qu'un correctif soit disponible.

Un correctif de sécurité peut être publié sans ADR publique préalable lorsque la
divulgation mettrait des déploiements en danger. Le cas échéant, une ADR documentant le
changement est publiée avec le correctif, et non après : le processus est différé, jamais
sauté. La sécurité est le seul motif pour lequel l'exigence d'ADR peut être différée.

## 7. Ouverture de la gouvernance

Une gouvernance à éditeur unique est une condition de départ, pas une destination. Les
critères suivants sont engagés d'avance pour que la transition ne soit pas laissée à la
discrétion du porteur au moment où elle devient gênante.

Un **comité de pilotage technique (TSC)** est établi dès que l'une des conditions est
remplie :

- **(a)** Trois personnes ou plus extérieures à Karako Systems ont chacune mené à bien des
  contributions substantielles et indépendantes (une ADR acceptée, un adaptateur
  d'opérateur, ou un SDK maintenu) et acceptent d'y siéger ; ou
- **(b)** Un opérateur de paiement, une institution financière ou une autorité publique
  adopte OpenFSP en production ou le soutient formellement.

À l'établissement :

- Le TSC détient l'autorité finale sur la spécification. L'éditeur y devient une voix parmi
  d'autres, pas au-dessus d'elles.
- Karako Systems détient au plus **un tiers** des sièges, arrondi à l'inférieur, quelle que
  soit la part du travail qu'elle a fournie. Un porteur que l'on ne peut pas mettre en
  minorité n'est pas de la gouvernance.
- Les sièges sont détenus par des personnes, nommées publiquement, avec leur affiliation
  déclarée.
- Les décisions se prennent à la majorité simple des membres siégeant, publiquement, avec
  des motifs consignés.
- Le nom OpenFSP est transféré selon le §8.
- La première tâche du TSC est de ratifier ou d'amender ce document.

D'ici là, cette section est l'engagement que la transition aura lieu, et sur quel
déclencheur.

## 8. Nom et marques de conformité

Le nom OpenFSP et toute marque de conformité (« OpenFSP conformant », ou une formule
voisine) sont détenus par Karako Systems et ne sont **pas** concédés par la licence
Apache-2.0, qui couvre le logiciel et le texte, pas les marques.

Karako Systems détient ce nom en **dépositaire**, pour le projet, et prend les engagements
suivants :

- **Aucun pouvoir d'octroi.** Karako Systems ne peut ni accorder ni refuser une revendication
  de conformité : les [règles d'usage du
  nom](https://github.com/openfspht/adrs/blob/main/spec/marques.md) en font une affaire de
  rapport publié, pour le porteur comme pour tous.
- **Aucune modification unilatérale.** Une fois le TSC établi (§7), les règles d'usage du nom
  et la présente section ne peuvent être modifiées qu'avec l'accord du TSC.
- **Transfert au TSC.** Dans les douze mois suivant l'établissement du TSC, Karako Systems
  cède gratuitement le nom OpenFSP, et toute marque déposée qui s'y rattache, à une entité
  juridique désignée par le TSC. Jusqu'à cette cession, Karako Systems concède au TSC une
  licence d'usage du nom gratuite et irrévocable, pour les besoins du projet.

Ces engagements valent que le nom soit déposé ou non : s'il ne l'est pas, ils portent sur son
usage ; s'il l'est, sur le titre lui-même.

Les règles d'usage du nom, en particulier ce qu'une implémentation doit réussir avant de
pouvoir revendiquer la conformité, sont posées par les [règles d'usage du
nom](https://github.com/openfspht/adrs/blob/main/spec/marques.md)
([ADR-0016](https://github.com/openfspht/adrs/blob/main/text/0016-conformance-marks-and-naming.md)),
qui s'appuient sur la [suite de
conformité](https://github.com/openfspht/adrs/blob/main/spec/conformite.md). En résumé : il
n'y a ni programme de certification, ni frais, ni concession. Une implémentation publie un
rapport de réussite reproductible issu de la suite, et peut alors énoncer exactement ce que
dit ce rapport, dans la forme que fixe leur §3. Les implémentations du porteur lui-même
revendiquent la conformité par cette voie et par aucune autre, ce que leur §6 garantit.

Tant que ADR-0016 n'est pas acceptée, décrivez votre travail comme « bâti sur OpenFSP » ou
« implémente OpenFSP `<version>` » : des descriptions factuelles, qu'aucune marque ne
restreint. Ces formulations restent disponibles ensuite, et ne sont pas des revendications
de conformité.

## 9. Amender ce document

Les changements apportés à ce document exigent une ADR acceptée, avec une exception :
l'établissement du TSC prévu à la section 7 se produit automatiquement dès que son déclencheur
est atteint, et ne nécessite pas d'ADR pour prendre effet.

---

*Questions sur la gouvernance : dev@karakosystems.com*
