# Contribuer à OpenFSP

Merci d'envisager une contribution. Ce document explique à quoi vous attendre, comment
proposer un changement, et la seule formalité juridique que nous demandons.

Lisez aussi [GOVERNANCE.md](GOVERNANCE.md) : il explique qui décide de quoi, et quelles
garanties vous recevez en échange de votre travail.

## À quoi s'attendre

OpenFSP est actuellement maintenu par une seule personne. Les relectures sont soignées mais
pas rapides, et une proposition substantielle peut attendre une semaine avant de recevoir
l'attention qu'elle mérite. Nous préférons vous le dire franchement plutôt que de vous
laisser dans le doute.

Les contributions petites et bien délimitées sont fusionnées le plus vite. Si vous préparez
quelque chose de gros, ouvrez d'abord un ticket : il est décourageant d'écrire mille lignes
et d'apprendre après coup que la conception partait ailleurs.

## Choisir sa voie

### Ouvrez un ticket quand

Vous avez trouvé un bogue, buté sur une ambiguïté de la spécification, ou voulez discuter
d'une idée avant d'y investir du temps. Les signalements d'ambiguïté ont une vraie valeur :
une phrase que deux implémenteurs lisent différemment est un défaut de la spécification,
même si toutes les implémentations se trouvent aujourd'hui d'accord.

### Ouvrez une pull request quand

Le changement ne modifie pas ce qu'une implémentation conforme doit faire : corrections de
bogues, performance, tests, documentation, exemples, outillage, un nouvel adaptateur
d'opérateur qui entre dans le modèle de capacités existant.

### Écrivez une ADR quand

Le changement touche au protocole, au modèle de capacités, à la conformité, au
versionnement, aux exigences de sécurité, ou au processus lui-même.
[Le processus ADR](https://github.com/openfspht/adrs) l'explique et fournit le gabarit.

La frontière est posée dans
[GOVERNANCE.md §4](GOVERNANCE.md#4-comment-les-décisions-sont-prises). Dans le doute, ouvrez
un ticket et posez la question : nous préférons répondre à une question que rejeter un
travail achevé.

## Signez votre travail : le DCO

OpenFSP n'utilise pas d'accord de licence de contributeur. Vous gardez le droit d'auteur
sur ce que vous écrivez, et vous ne cédez rien à personne. En échange, nous vous demandons
de certifier que vous avez le droit d'apporter ce que vous apportez, au moyen du
[Developer Certificate of Origin](DCO).

Ajoutez une ligne `Signed-off-by` à chaque commit :

```bash
git commit -s -m "fix: reject a payment amount of zero"
```

ce qui ajoute :

```
Signed-off-by: Votre Nom <votre.email@example.com>
```

Utilisez votre vrai nom et une adresse électronique réelle. Si vous avez oublié sur le
dernier commit :

```bash
git commit --amend -s --no-edit
```

Pour toute une branche :

```bash
git rebase --signoff main
```

Les commits sans signature ne peuvent pas être fusionnés. Ce n'est pas de la bureaucratie
pour elle-même : c'est ce qui permet à OpenFSP d'accepter votre contribution sous
Apache-2.0 sans vous demander d'abandonner vos droits.

## Conventions du dépôt

**Langue.** La spécification est rédigée en français : c'est la langue de son marché, de
son régulateur et des développeurs qu'elle vise en premier. Le code, les identifiants, les
commentaires, les messages de commit et les tickets sont en anglais, parce qu'ils sont lus
par des implémenteurs de partout. La discussion en français ou en créole haïtien est la
bienvenue partout où elle vous aide à être précis.

**Messages de commit.** Conventional Commits : `type(portée): résumé à l'impératif`, en
anglais. Types : `feat`, `fix`, `docs`, `test`, `refactor`, `perf`, `build`, `ci`, `chore`,
`adr`. Rédigez le résumé de sorte qu'il complète la phrase « this commit will… ».

**Portée d'une pull request.** Un seul sujet par pull request. Une correction et un
remaniement dans la même branche rendent les deux plus difficiles à relire et plus
difficiles à annuler.

**Tests.** Tout changement du comportement spécifié s'accompagne d'un test de conformité.
Toute correction de bogue s'accompagne d'un test qui échoue sans la correction. Cela vaut pour
la passerelle comme pour les SDK, et ce n'est pas négociable dans un projet de paiement, où un
cas limite non testé finit par coûter de l'argent à quelqu'un.

## Signaler une vulnérabilité

N'ouvrez pas de ticket public. Suivez [SECURITY.md](SECURITY.md).

## Code de conduite

La participation est régie par le [code de conduite](CODE_OF_CONDUCT.md). Signalez vos
préoccupations à conduct@karakosystems.com.

## Licence

En contribuant, vous acceptez que votre contribution soit publiée sous la
[licence Apache 2.0](LICENSE), la licence qui couvre l'ensemble du projet.
