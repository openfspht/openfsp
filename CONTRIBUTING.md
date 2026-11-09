# Contribuer à OpenFSP

Qui décide de quoi et quelles garanties s'appliquent : [GOVERNANCE.md](GOVERNANCE.md).

## Délais

Je suis le seul éditeur d'OpenFSP ([GOVERNANCE.md 1](GOVERNANCE.md#1-porteur)). Je vise
une première relecture sous 7 jours et je relis en premier les contributions courtes,
limitées à un seul sujet. Pour un changement important, ouvrez d'abord un ticket.

## Choisir sa voie

### Ouvrez un ticket quand

Vous avez trouvé un bogue, une ambiguïté de la spécification, ou voulez discuter d'une idée.
Je tiens pour un défaut de la spécification toute phrase que deux implémenteurs lisent
différemment, même si les implémentations actuelles concordent.

### Ouvrez une pull request quand

Le changement ne modifie pas ce qu'une implémentation conforme doit faire. Liste :
[GOVERNANCE.md 4](GOVERNANCE.md#4-comment-les-décisions-sont-prises), *ADR non requise*.

### Écrivez une ADR quand

Le changement modifie ce qu'une implémentation conforme doit faire. Liste :
[GOVERNANCE.md 4](GOVERNANCE.md#4-comment-les-décisions-sont-prises), *ADR requise*.
Gabarit et procédure : [processus ADR](https://github.com/openfspht/adrs).

## Developer Certificate of Origin (DCO)

OpenFSP n'utilise pas d'accord de licence de contributeur : vous gardez le droit d'auteur.
Chaque commit certifie le [Developer Certificate of Origin](DCO) par une ligne
`Signed-off-by` :

```bash
git commit -s -m "fix: reject a payment amount of zero"
```

ce qui ajoute :

```
Signed-off-by: Votre Nom <votre.email@example.com>
```

Utilisez votre vrai nom et une adresse électronique réelle. Pour signer le dernier commit
après coup :

```bash
git commit --amend -s --no-edit
```

Pour toute une branche :

```bash
git rebase --signoff main
```

Je ne fusionne pas les commits sans signature.

## Conventions du dépôt

**Langue.** La spécification et les documents de gouvernance sont rédigés en français. Le code, les identifiants, les commentaires et les messages de commit sont en
anglais. Les tickets sont rédigés de préférence en anglais ; je lis aussi le français et le créole
haïtien dans les tickets, les pull requests et les fils d'ADR.

**Messages de commit.** Conventional Commits : `type(portée): résumé à l'impératif`, en
anglais. Types : `feat`, `fix`, `docs`, `test`, `refactor`, `perf`, `build`, `ci`, `chore`,
`adr`.

**Portée d'une pull request.** Un seul sujet par pull request.

**Tests.** Tout changement du comportement spécifié s'accompagne d'un test de conformité.
Toute correction de bogue s'accompagne d'un test qui échoue sans la correction. Cela vaut pour
la passerelle comme pour les SDK.

## Signaler une vulnérabilité

N'ouvrez pas de ticket public. Suivez [SECURITY.md](SECURITY.md).

## Code de conduite

La participation est régie par le [code de conduite](CODE_OF_CONDUCT.md). Signalements :
conduct@karakosystems.com.

## Licence

Toute contribution est publiée sous la [licence Apache 2.0](LICENSE).
