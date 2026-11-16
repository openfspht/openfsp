import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
  CopyButton,
  Muted,
  Tabular,
} from "@workspace/design-system"
import type { SimulatorConfig } from "@workspace/types"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@workspace/ui/components/table"

import { useI18n } from "@/i18n/i18n-provider"

function Copyable({ value }: { value: string }) {
  return (
    <span className="flex items-center gap-2">
      <Tabular className="min-w-0 break-all">{value}</Tabular>
      <CopyButton text={value} size="xs" />
    </span>
  )
}

export function SimulatorConfigCard({ config }: { config: SimulatorConfig }) {
  const { t } = useI18n()
  const copy = t.setup
  return (
    <Card>
      <CardHeader>
        <CardTitle className="font-heading">{copy.title}</CardTitle>
        <CardDescription>{copy.description}</CardDescription>
      </CardHeader>
      <CardContent className="space-y-6">
        <dl className="grid gap-x-6 gap-y-2 text-sm sm:grid-cols-[auto_minmax(0,1fr)]">
          <dt className="text-muted-foreground">{copy.space}</dt>
          <dd>
            <Tabular className="break-all">{config.space}</Tabular>
          </dd>
          <dt className="text-muted-foreground">{copy.environment}</dt>
          <dd>
            <Tabular>{config.environment}</Tabular>
          </dd>
          <dt className="text-muted-foreground">{copy.keyPrefix}</dt>
          <dd>
            <Tabular>{config.key_prefix}</Tabular>
          </dd>
          <dt className="text-muted-foreground">{copy.webhookUrl}</dt>
          <dd className="space-y-1">
            <Copyable value={config.webhook_url} />
            <Muted className="block text-xs">{copy.webhookHint}</Muted>
          </dd>
        </dl>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>{copy.provider}</TableHead>
              <TableHead>{copy.baseUrl}</TableHead>
              <TableHead>{copy.credentials}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody striped>
            {config.providers.map((provider) => (
              <TableRow key={provider.id}>
                <TableCell className="align-top">
                  <Tabular className="block font-medium">{provider.id}</Tabular>
                  <Muted className="text-xs">{provider.display_name}</Muted>
                </TableCell>
                <TableCell className="space-y-2 align-top whitespace-normal">
                  <Copyable value={provider.base_url} />
                </TableCell>
                <TableCell className="align-top whitespace-normal">
                  <dl className="space-y-1 text-xs">
                    {Object.entries(provider.credentials).map(
                      ([name, value]) => (
                        <div key={name}>
                          <dt className="text-muted-foreground">{name}</dt>
                          <dd>
                            <Copyable value={value} />
                          </dd>
                        </div>
                      )
                    )}
                  </dl>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  )
}
