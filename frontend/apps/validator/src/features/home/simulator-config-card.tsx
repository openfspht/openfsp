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
import type { SimulatorConfig, ValidatorTarget } from "@workspace/types"
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

export function SimulatorConfigCard({
  config,
  target,
}: {
  config: SimulatorConfig
  target: ValidatorTarget
}) {
  const { t } = useI18n()
  const copy = t.setup
  const gateway = target === "gateway"
  return (
    <Card>
      <CardHeader>
        <CardTitle className="font-heading">
          {gateway ? copy.title : copy.nativeTitle}
        </CardTitle>
        <CardDescription>
          {gateway ? copy.description : copy.nativeDescription}
        </CardDescription>
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
          {gateway && config.derivation_key && (
            <>
              <dt className="text-muted-foreground">{copy.derivationKey}</dt>
              <dd>
                <Copyable value={config.derivation_key} />
              </dd>
            </>
          )}
          {gateway && (
            <>
              <dt className="text-muted-foreground">{copy.webhookUrl}</dt>
              <dd className="space-y-1">
                <Copyable value={config.webhook_url} />
                <Muted className="block text-xs">{copy.webhookHint}</Muted>
              </dd>
            </>
          )}
        </dl>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>{copy.operator}</TableHead>
              <TableHead>{copy.baseUrl}</TableHead>
              <TableHead>{copy.credentials}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody striped>
            {config.operators.map((operator) => (
              <TableRow key={operator.id}>
                <TableCell className="align-top">
                  <Tabular className="block font-medium">{operator.id}</Tabular>
                  <Muted className="text-xs">
                    {operator.display_name ?? operator.dialect}
                  </Muted>
                </TableCell>
                <TableCell className="space-y-2 align-top whitespace-normal">
                  <Copyable value={operator.base_url} />
                  {operator.secondary_url && (
                    <div className="space-y-1">
                      <Muted className="text-xs">{copy.secondaryUrl}</Muted>
                      <Copyable value={operator.secondary_url} />
                    </div>
                  )}
                </TableCell>
                <TableCell className="align-top whitespace-normal">
                  <dl className="space-y-1 text-xs">
                    {Object.entries(operator.credentials).map(
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
