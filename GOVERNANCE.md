# Gouvernance

OpenFSP est d'abord mon projet de recherche de master. Je fixe ici qui décide de quoi,
comment je consigne les décisions, quelles garanties reçoivent les adoptants, et à quelles
conditions j'ouvre la gouvernance.

## 1. Porteur

OpenFSP est édité et porté par **Karako Systems** (Haïti, https://karakosystems.com), qui
détient la gouvernance du projet, ses dépôts et son nom.

Karako Systems nomme l'**éditeur**, décideur technique du projet, responsable de la
cohérence de la spécification, des ADR et de la publication de la suite de
conformité.

**Je suis aujourd'hui le seul éditeur, sans autre mainteneur.** Conditions d'ouverture : 7.

## 2. Intérêt déclaré

Karako Systems construit des produits commerciaux au-dessus d'OpenFSP. Cet intérêt est
encadré par quatre contraintes :

1. **Aucune voie privilégiée.** Les changements motivés par un produit commercial du
   porteur passent par le même processus ADR public que tout autre changement, sous la même
   relecture, dans les mêmes délais.
2. **Motivation déclarée.** Quand une ADR est poussée par un besoin d'un produit du
   porteur, sa section *Décision* le dit.
3. **Aucun verrouillage par la spécification.** La spécification ne doit pas contenir
   d'exigence dont le seul objet serait d'avantager une implémentation. Tout relecteur
   peut contester une disposition sur ce motif, et je m'engage à répondre publiquement à
   la contestation avant de publier la disposition.
4. **Parité d'accès aux fonctionnalités.** Toute capacité qu'utilisent les produits du
   porteur fait partie de la spécification publique. Il n'existe aucune extension non
   documentée.

Référence : l'article 85 de la loi du 14 mai 2012 sur les banques et autres institutions
financières oblige les établissements réglementés à agir « loyalement et équitablement au
mieux des intérêts de leurs clients et de l'intégrité du marché », et à « s'efforcer
d'écarter les conflits d'intérêt et, lorsque ces derniers ne peuvent être évités, à veiller à
ce que leurs clients soient traités équitablement ». Karako Systems n'est pas une banque et
cet article ne la lie pas ; les quatre contraintes en reprennent la forme.

## 3. Engagement de licence

Engagements de Karako Systems envers tous les adoptants d'OpenFSP :

- **Tout ce qui est publié sous OpenFSP est sous licence Apache-2.0** : spécification,
  passerelle, serveur simulé, SDK et suite de conformité. Aucun composant n'est
  retenu sous une autre licence.
- **Aucun accord de licence de contributeur.** Les contributions relèvent du
  Developer Certificate of Origin (voir [CONTRIBUTING.md](CONTRIBUTING.md)). Les
  contributeurs conservent leur droit d'auteur ; aucun droit n'est cédé au porteur.
- **Concession irrévocable.** Les contributions n'étant cédées à aucun
  propriétaire unique, aucune décision du porteur ne peut retirer rétroactivement la
  concession Apache-2.0 sur ce qui a été publié.
- **Droit de fork.** La licence Apache-2.0 permet à quiconque de dériver le projet ; le
  porteur reconnaît ce recours comme légitime.

## 4. Comment les décisions sont prises

### ADR requise

Une ADR (voir [`adrs`](https://github.com/openfspht/adrs)) est requise pour tout ce qui
change ce qu'une implémentation conforme doit faire :

- Tout changement des messages échangés entre client et passerelle : ressources, champs, valeurs de statut, codes
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
  capacités existant. Sinon, une ADR.
- La documentation, les exemples, les tests, l'outillage, les traductions.
- Les corrections éditoriales d'une ADR qui n'en changent pas le sens.

En cas de doute sur la voie, ouvrez un ticket et posez la question.

### Résolution

Je décide publiquement, dans le fil de l'ADR, avec des motifs. Je recherche le consensus,
sans en faire une exigence formelle tant que je suis le seul éditeur.

## 5. Garanties de stabilité

- **Versionnement sémantique de la spécification**
  ([SemVer 2.0.0](https://semver.org/spec/v2.0.0.html)), `MAJEUR.MINEUR.CORRECTIF`. Un
  incrément MAJEUR peut casser les implémentations conformes ; un incrément MINEUR ajoute une
  capacité en restant rétrocompatible. Adaptation : un incrément CORRECTIF clarifie la
  formulation sans changer le comportement.
- **La rétrocompatibilité n'est pas rompue sans une ADR.** Aucune exception pour
  l'urgence.
- **Une dépréciation exige un préavis d'une version MAJEURE complète.** Ce qui est déprécié
  en `2.x` ne peut être retiré avant `3.0`, et doit être marqué comme déprécié dans la
  spécification, dans les réponses de la passerelle et dans les notes de version pendant
  toute cette durée.
- **Les errata sont tracés.** Une formulation publiée fausse ou ambiguë est corrigée sur
  place par un commit qui le dit.
- **Exception avant 1.0.** Avant `1.0.0`, des changements cassants peuvent survenir entre
  versions MINEURES. Chacun exige une ADR.

## 6. Gouvernance de la sécurité

Les signalements de vulnérabilité suivent [SECURITY.md](SECURITY.md) et sont traités en
privé jusqu'à la disponibilité d'un correctif.

Un correctif de sécurité peut être publié sans ADR publique préalable lorsque la
divulgation mettrait des déploiements en danger. L'ADR documentant le changement est alors
publiée avec le correctif. La sécurité est le seul motif pour lequel l'exigence
d'ADR peut être différée.

## 7. Ouverture de la gouvernance

Un **comité de pilotage technique (TSC)** est établi dès que l'une des conditions est
remplie :

- **(a)** Trois personnes ou plus extérieures à Karako Systems ont chacune mené à bien des
  contributions substantielles et indépendantes (une ADR, un adaptateur
  d'opérateur, ou un SDK maintenu) et acceptent d'y siéger ; ou
- **(b)** Un opérateur de paiement, une institution financière ou une autorité publique
  adopte OpenFSP en production ou le soutient formellement.

À l'établissement :

- Le TSC détient l'autorité finale sur la spécification. L'éditeur y a une voix comme les
  autres membres.
- Karako Systems détient au plus **un tiers** des sièges, arrondi à l'inférieur, quelle que
  soit la part du travail qu'elle a fournie.
- Les sièges sont détenus par des personnes, nommées publiquement, avec leur affiliation
  déclarée.
- Les décisions se prennent à la majorité simple des membres siégeant, publiquement, avec
  des motifs consignés.
- Le nom OpenFSP est transféré selon le 8.
- La première tâche du TSC est de ratifier ou d'amender ce document.

## 8. Nom et marques de conformité

Le nom OpenFSP et toute marque de conformité (« OpenFSP conformant », ou une formule
voisine) sont détenus par Karako Systems et ne sont pas concédés par la licence
Apache-2.0, qui couvre le logiciel et le texte, pas les marques.

Karako Systems détient ce nom en **dépositaire**, pour le projet, et s'engage :

- **Aucun pouvoir d'octroi.** Karako Systems ne peut ni accorder ni refuser une revendication
  de conformité : selon les règles ci-dessous, elle dépend d'un rapport publié, pour le
  porteur comme pour tous.
- **Aucune modification unilatérale.** Une fois le TSC établi (7), les règles d'usage du nom
  et la présente section ne peuvent être modifiées qu'avec l'accord du TSC.
- **Transfert au TSC.** Dans les douze mois suivant l'établissement du TSC, Karako Systems
  cède gratuitement le nom OpenFSP, et toute marque déposée qui s'y rattache, à une entité
  juridique désignée par le TSC. Jusqu'à cette cession, Karako Systems concède au TSC une
  licence d'usage du nom gratuite et irrévocable, pour les besoins du projet.

Ces engagements valent que le nom soit déposé ou non : s'il ne l'est pas, ils portent sur son
usage ; s'il l'est, sur le titre lui-même.

**Revendiquer la conformité.** Il n'y a ni programme de certification, ni frais, ni
concession :

- Une revendication affirme un résultat de la [suite de
  conformité](https://github.com/openfspht/adrs/blob/main/spec/conformite.md). Elle repose sur
  un rapport de réussite publié avec sa configuration, pour la version revendiquée de
  l'implémentation. Chacun exécute la suite lui-même : ni soumission, ni approbation.
- Forme : `OpenFSP conformant <cible>, <niveau>, protocol <version>`, suivie des profils
  réussis, avec un accès au rapport. Forme abrégée admise : « OpenFSP conformant (Core,
  0.1.0) », jamais « OpenFSP conformant » seul. Le mot est « conformant », jamais certified,
  approved, validated, endorsed, accredited ni compliant.
- Interdit : laisser entendre qu'un tiers a certifié ou approuvé, omettre la version, affirmer
  un profil non réussi, présenter la conformité comme une assurance de sécurité, faire passer un
  produit pour un produit du projet, se dire conforme parce qu'un adaptateur existe.
- Une revendication fausse est d'abord notifiée à son auteur, puis signalée publiquement avec
  les preuves si elle n'est pas corrigée. Il n'y a ni octroi ni révocation : une revendication
  est invalide dès que le rapport publié ne peut plus être reproduit avec la configuration
  déclarée.
- Les implémentations du porteur suivent exactement cette voie, publiées au même endroit.

Décrire son travail comme « bâti sur OpenFSP » ou « implémente OpenFSP `<version>` » reste
libre : ce ne sont pas des revendications de conformité.

## 9. Amender ce document

Les changements apportés à ce document exigent une ADR, avec une exception :
l'établissement du TSC prévu à la section 7 se produit automatiquement dès que son déclencheur
est atteint, et ne nécessite pas d'ADR pour prendre effet.

---

*Questions sur la gouvernance : dev@karakosystems.com*
