import type { Money as MoneyValue } from "@workspace/types"

import { Button, Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle, Money } from "../atoms"

interface ProductCardProps {
  name: string
  description: string
  price: MoneyValue
  onBuy: () => void
  buyLabel: string
  disabled?: boolean
}

export function ProductCard({ name, description, price, onBuy, buyLabel, disabled }: ProductCardProps) {
  return (
    <Card className="flex flex-col">
      <CardHeader>
        <CardTitle className="font-heading">{name}</CardTitle>
        <CardDescription>{description}</CardDescription>
      </CardHeader>
      <CardContent className="mt-auto">
        <Money value={price} className="font-heading text-2xl font-bold" />
      </CardContent>
      <CardFooter>
        <Button className="w-full" onClick={onBuy} disabled={disabled}>
          {buyLabel}
        </Button>
      </CardFooter>
    </Card>
  )
}
