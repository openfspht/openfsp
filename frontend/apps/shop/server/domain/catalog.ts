import type { Money } from "@workspace/types"

export type Text = { en: string; fr: string }

export type Aisle = "coffee" | "cacao" | "fruit" | "spirits" | "pantry"

export interface Product {
  id: string
  aisle: Aisle
  name: Text
  description: Text
  origin: Text
  unit: Text
  price: Money
}

const htg = (gourdes: number): Money => ({
  amount: gourdes * 100,
  currency: "HTG",
})

export const catalog: Product[] = [
  {
    id: "coffee",
    aisle: "coffee",
    name: { en: "Thiotte coffee", fr: "Café de Thiotte" },
    description: {
      en: "Typica beans grown under shade on the Massif de la Selle, roasted medium the week they ship.",
      fr: "Typica cultivé sous ombrage sur le massif de la Selle, torréfié moyen la semaine de l'envoi.",
    },
    origin: {
      en: "Thiotte, South-East, 1,450 m",
      fr: "Thiotte, Sud-Est, 1 450 m",
    },
    unit: { en: "1 kg bag", fr: "sac de 1 kg" },
    price: htg(350),
  },
  {
    id: "cacao",
    aisle: "cacao",
    name: { en: "Cacao nibs", fr: "Éclats de cacao" },
    description: {
      en: "Fermented Trinitario cacao, cracked and winnowed, for baking or the morning chocolate.",
      fr: "Cacao Trinitario fermenté, concassé et vanné, pour la pâtisserie ou le chocolat du matin.",
    },
    origin: { en: "Grande-Anse", fr: "Grande-Anse" },
    unit: { en: "500 g pouch", fr: "sachet de 500 g" },
    price: htg(175),
  },
  {
    id: "chocolate",
    aisle: "cacao",
    name: { en: "Dark chocolate", fr: "Chocolat noir" },
    description: {
      en: "Six bars at 70 % from Grande-Anse cacao, a little cane sugar and nothing else.",
      fr: "Six tablettes à 70 % de cacao de la Grande-Anse, un peu de sucre de canne, rien d'autre.",
    },
    origin: { en: "Grande-Anse", fr: "Grande-Anse" },
    unit: { en: "6 bars of 80 g", fr: "6 tablettes de 80 g" },
    price: htg(225),
  },
  {
    id: "mangoes",
    aisle: "fruit",
    name: { en: "Francique mangoes", fr: "Mangues francisque" },
    description: {
      en: "A crate picked ripe in the Artibonite, sweet and without strings.",
      fr: "Une caisse cueillie mûre dans l'Artibonite, sucrée et sans fils.",
    },
    origin: { en: "Artibonite", fr: "Artibonite" },
    unit: { en: "crate of 12", fr: "caisse de 12" },
    price: htg(125),
  },
  {
    id: "rum",
    aisle: "spirits",
    name: { en: "Aged cane rum", fr: "Rhum de canne vieilli" },
    description: {
      en: "Distilled from cane juice and aged eight years in oak.",
      fr: "Distillé de jus de canne et vieilli huit ans en fût de chêne.",
    },
    origin: { en: "Plaine du Cul-de-Sac", fr: "Plaine du Cul-de-Sac" },
    unit: { en: "70 cl bottle", fr: "bouteille de 70 cl" },
    price: htg(600),
  },
  {
    id: "epis",
    aisle: "pantry",
    name: { en: "Epis", fr: "Épis" },
    description: {
      en: "The green seasoning base of Haitian cooking: parsley, scallion, garlic, thyme and pepper.",
      fr: "La base verte de la cuisine haïtienne : persil, cive, ail, thym et piment.",
    },
    origin: { en: "Kenscoff", fr: "Kenscoff" },
    unit: { en: "350 g jar", fr: "pot de 350 g" },
    price: htg(75),
  },
  {
    id: "pikliz",
    aisle: "pantry",
    name: { en: "Pikliz", fr: "Pikliz" },
    description: {
      en: "Cabbage, carrot and scotch bonnet pickled in sour orange. Hot.",
      fr: "Chou, carotte et piment bouc confits à l'orange amère. Fort.",
    },
    origin: { en: "Port-au-Prince", fr: "Port-au-Prince" },
    unit: { en: "500 g jar", fr: "pot de 500 g" },
    price: htg(60),
  },
  {
    id: "honey",
    aisle: "pantry",
    name: { en: "Wildflower honey", fr: "Miel de fleurs" },
    description: {
      en: "Raw honey from hives among coffee and citrus trees.",
      fr: "Miel cru de ruches installées entre caféiers et agrumes.",
    },
    origin: { en: "Plateau Central", fr: "Plateau Central" },
    unit: { en: "500 g jar", fr: "pot de 500 g" },
    price: htg(190),
  },
]
