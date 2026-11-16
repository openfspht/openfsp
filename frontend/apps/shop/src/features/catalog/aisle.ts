import type { Aisle } from "@/api/shop"

export const AISLES: readonly Aisle[] = ["coffee", "cacao", "fruit", "spirits", "pantry"]

export const PAPER: Record<Aisle, string> = {
  coffee: "paper--mango",
  cacao: "paper--hibiscus",
  fruit: "paper--leaf",
  spirits: "paper--sea",
  pantry: "paper--sun",
}
