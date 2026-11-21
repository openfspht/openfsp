import { useQuery } from "@tanstack/react-query"
import {
    Button,
    Card,
    CardContent,
    CardDescription,
    CardHeader,
    CardTitle,
    CopyButton,
    EmptyText,
    ErrorText,
    Field,
    Heading,
    Muted,
    PageHeader,
    Tabular,
    Time,
} from "@workspace/design-system"
import type { AttestationResource, AttestationVerification } from "@workspace/types"
import { cn } from "@workspace/ui/lib/utils"
import {
    CircleCheck,
    CircleDashed,
    CircleX,
    Download,
    FileText,
    OctagonAlert,
    ShieldCheck,
} from "lucide-react"
import { Link, useParams } from "react-router"

import { queryKeys } from "@/api/query-keys"
import { problemOf, validatorApi } from "@/api/validator-api"
import { CoreTally } from "@/features/run/core-tally"
import { LimitsCard } from "@/features/run/limits-card"
import { CORE_LEVEL } from "@/features/run/run-state"
import { LevelBadge } from "@/features/run/verdict-badge"
import { useI18n } from "@/i18n/i18n-provider"

import { badgeAlt, badgeHtml, badgeMarkdown, checksOf } from "./attestation-model"

export function AttestationPage() {
    const { id = "" } = useParams()
    const { t, locale } = useI18n()
    const attestation = useQuery({
        queryKey: queryKeys.attestation(id),
        queryFn: () => validatorApi.attestation(id),
        retry: false,
    })
    const verification = useQuery({
        queryKey: queryKeys.verification(id),
        queryFn: () => validatorApi.verifyAttestation(id),
        enabled: attestation.isSuccess,
        retry: false,
    })
    const suite = useQuery({
        queryKey: queryKeys.suite,
        queryFn: validatorApi.suite,
    })
    const data = attestation.data
    const missing = problemOf(attestation.error)?.status === 404
    return (
        <>
            <PageHeader
                title={t.attestation.title}
                description={data?.attestation.target.base_url}
                actions={
                    data && (
                        <>
                            <Button
                                render={
                                    <a
                                        href={`${data.links.pdf}?lang=${encodeURIComponent(locale)}`}
                                        download
                                    />
                                }
                            >
                                <Download />
                                {t.attestation.downloadPdf}
                            </Button>
                            <Button
                                variant="outline"
                                render={
                                    <a
                                        href={`/api/attestations/${encodeURIComponent(id)}`}
                                        download={`openfsp-attestation-${id}.json`}
                                    />
                                }
                            >
                                <FileText />
                                {t.attestation.downloadJson}
                            </Button>
                            <Button
                                variant="outline"
                                render={<Link to={`/reports/${data.attestation.report.id}`} />}
                            >
                                {t.attestation.openReport}
                            </Button>
                        </>
                    )
                }
            />
            {attestation.isPending && <EmptyText>{t.attestation.loading}</EmptyText>}
            {missing && <ErrorText>{t.attestation.notFound}</ErrorText>}
            {attestation.isError && !missing && (
                <ErrorText>
                    {problemOf(attestation.error)?.detail ??
                        problemOf(attestation.error)?.title ??
                        t.unreachable}
                </ErrorText>
            )}
            {data && (
                <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_22rem] lg:items-start">
                    <div className="min-w-0 space-y-6">
                        <CoreBanner resource={data} />
                        <Muted className="block text-sm">{t.attestation.statement}</Muted>
                        <ProfilesCard resource={data} />
                        <DetailsCard resource={data} />
                        <LimitsCard
                            limits={data.attestation.limits}
                            specUrl={suite.data?.spec_url}
                        />
                        <Card>
                            <CardHeader>
                                <CardTitle className="font-heading">
                                    {t.attestation.validity}
                                </CardTitle>
                            </CardHeader>
                            <CardContent className="text-sm">
                                {t.attestation.validityStatement}{" "}
                                <Muted>
                                    <Tabular>({data.attestation.validity.reference})</Tabular>
                                </Muted>
                            </CardContent>
                        </Card>
                    </div>
                    <aside className="space-y-6">
                        <VerificationCard
                            resource={data}
                            verification={verification.data}
                            pending={verification.isPending}
                        />
                        <BadgeCard resource={data} />
                    </aside>
                </div>
            )}
        </>
    )
}

const BANNERS = {
    PASS: {
        icon: ShieldCheck,
        box: "border-emerald-500/40 bg-emerald-500/10",
        iconTone: "text-emerald-600",
        title: "text-emerald-700 dark:text-emerald-400",
    },
    INCOMPLETE: {
        icon: CircleDashed,
        box: "border-brand-accent/50 bg-brand-accent/10",
        iconTone: "text-accent-text",
        title: "text-accent-text",
    },
    FAIL: {
        icon: OctagonAlert,
        box: "border-red-500/50 bg-red-500/10",
        iconTone: "text-red-600",
        title: "text-red-700 dark:text-red-400",
    },
} as const

function CoreBanner({ resource }: { resource: AttestationResource }) {
    const { t } = useI18n()
    const verdict = resource.attestation.verdicts.core
    const kind = verdict === "PASS" ? "PASS" : verdict === "FAIL" ? "FAIL" : "INCOMPLETE"
    const style = BANNERS[kind]
    const copy = {
        PASS: [t.attestation.corePassed, t.attestation.corePassedDetail],
        INCOMPLETE: [t.attestation.coreIncomplete, t.attestation.coreIncompleteDetail],
        FAIL: [t.attestation.coreFailed, t.attestation.coreFailedDetail],
    }[kind]
    const Icon = style.icon
    return (
        <div
            role={kind === "PASS" ? "status" : "alert"}
            className={cn("flex items-start gap-4 rounded-lg border p-5", style.box)}
        >
            <Icon className={cn("mt-0.5 size-7 shrink-0", style.iconTone)} />
            <div className="min-w-0 flex-1 space-y-1">
                <p className={cn("font-heading text-xl font-bold", style.title)}>{copy[0]}</p>
                <p className="text-sm">{copy[1]}</p>
                {resource.attestation.verdicts.core_results && (
                    <div className="pt-2">
                        <CoreTally
                            counts={resource.attestation.verdicts.core_results}
                            core={resource.attestation.verdicts.core}
                        />
                    </div>
                )}
            </div>
        </div>
    )
}

function ProfilesCard({ resource }: { resource: AttestationResource }) {
    const { t } = useI18n()
    const { claim, verdicts } = resource.attestation
    return (
        <Card>
            <CardHeader>
                <CardTitle className="font-heading">{t.summary.claim}</CardTitle>
                <CardDescription>
                    {claim.level === CORE_LEVEL ? (
                        <Tabular>
                            {[claim.target, claim.level, ...claim.profiles].join(" + ")},{" "}
                            {t.summary.protocol(claim.protocol_version)}
                        </Tabular>
                    ) : (
                        t.summary.noClaim
                    )}
                </CardDescription>
            </CardHeader>
            <CardContent>
                <Heading className="mb-2 text-muted-foreground">{t.summary.profiles}</Heading>
                {verdicts.profiles.length > 0 ? (
                    <ul className="grid gap-x-8 gap-y-2 sm:grid-cols-2">
                        {verdicts.profiles.map((profile) => (
                            <li
                                key={profile.name}
                                className="flex items-center justify-between gap-4 border-b pb-2 text-sm"
                            >
                                <Tabular className="break-all">{profile.name}</Tabular>
                                <LevelBadge verdict={profile.verdict} />
                            </li>
                        ))}
                    </ul>
                ) : (
                    <EmptyText>{t.summary.noProfiles}</EmptyText>
                )}
            </CardContent>
        </Card>
    )
}

function VerificationCard({
    resource,
    verification,
    pending,
}: {
    resource: AttestationResource
    verification?: AttestationVerification
    pending: boolean
}) {
    const { t } = useI18n()
    const copy = t.attestation
    return (
        <Card>
            <CardHeader>
                <CardTitle className="font-heading">{copy.verification}</CardTitle>
                {verification && (
                    <CardDescription>
                        <span
                            className={cn(
                                "font-medium",
                                verification.valid ? "text-emerald-600" : "text-red-600"
                            )}
                        >
                            {verification.valid ? copy.verified : copy.notVerified}
                        </span>
                    </CardDescription>
                )}
            </CardHeader>
            <CardContent className="space-y-4 text-sm">
                {pending && <EmptyText>{copy.verifying}</EmptyText>}
                {verification && (
                    <ul className="space-y-2">
                        {checksOf(verification).map(({ check, ok }) => (
                            <li key={check} className="flex items-center gap-2">
                                {ok ? (
                                    <CircleCheck className="size-4 text-emerald-600" />
                                ) : (
                                    <CircleX className="size-4 text-red-600" />
                                )}
                                <span>{copy.checks[check][ok ? 0 : 1]}</span>
                                {check === "key" && verification.key_status && (
                                    <Muted>({copy.keyStatus(verification.key_status)})</Muted>
                                )}
                            </li>
                        ))}
                    </ul>
                )}
                {verification && verification.problems.length > 0 && (
                    <ul className="space-y-1">
                        {verification.problems.map((problem) => (
                            <li key={problem}>
                                <ErrorText>{problem}</ErrorText>
                            </li>
                        ))}
                    </ul>
                )}
                <p className="text-muted-foreground">
                    {copy.offline}{" "}
                    <a
                        href={resource.links.keys}
                        className="underline underline-offset-4 hover:text-foreground"
                    >
                        <Tabular>{resource.links.keys}</Tabular>
                    </a>
                </p>
            </CardContent>
        </Card>
    )
}

function BadgeCard({ resource }: { resource: AttestationResource }) {
    const { t } = useI18n()
    const copy = t.attestation
    const snippets = [
        { label: copy.markdown, value: badgeMarkdown(resource) },
        { label: copy.html, value: badgeHtml(resource) },
    ]
    return (
        <Card>
            <CardHeader>
                <CardTitle className="font-heading">{copy.badge}</CardTitle>
                <CardDescription>{copy.badgeHint}</CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
                <img src={resource.links.badge} alt={badgeAlt(resource)} />
                {snippets.map((snippet) => (
                    <div key={snippet.label} className="space-y-1">
                        <div className="flex items-center justify-between gap-2">
                            <Muted className="text-xs">{snippet.label}</Muted>
                            <CopyButton text={snippet.value} size="xs" />
                        </div>
                        <pre className="overflow-x-auto rounded-md bg-muted p-2 text-xs break-all whitespace-pre-wrap">
                            {snippet.value}
                        </pre>
                    </div>
                ))}
            </CardContent>
        </Card>
    )
}

function DetailsCard({ resource }: { resource: AttestationResource }) {
    const { t } = useI18n()
    const copy = t.attestation
    const document = resource.attestation
    return (
        <Card>
            <CardHeader>
                <CardTitle className="font-heading">{copy.details}</CardTitle>
            </CardHeader>
            <CardContent className="space-y-6">
                <dl className="grid gap-2 md:grid-cols-2">
                    <Field label={copy.target}>
                        {t.home.targets[document.target.kind as keyof typeof t.home.targets] ??
                            document.target.kind}
                    </Field>
                    <Field label={copy.baseUrl}>
                        <Tabular>{document.target.base_url}</Tabular>
                    </Field>
                    <Field label={copy.protocol}>
                        <Tabular>
                            {document.target.protocol_version ?? t.report.unknownProtocol}
                        </Tabular>
                    </Field>
                    <Field label={copy.suite}>
                        <Tabular>
                            {document.suite.version} / {document.suite.protocol_version}
                        </Tabular>
                    </Field>
                    <Field label={copy.runDate}>
                        <Time value={document.run.date} />
                    </Field>
                    <Field label={copy.issuedAt}>
                        <Time value={document.issued_at} />
                    </Field>
                    <Field label={copy.issuer}>
                        <Tabular>{document.issuer}</Tabular>
                    </Field>
                    <Field label={copy.id}>
                        <Tabular>{document.id}</Tabular>
                    </Field>
                    <Field label={copy.report}>
                        <Link
                            to={`/reports/${document.report.id}`}
                            className="underline-offset-4 hover:underline"
                        >
                            <Tabular>{document.report.id}</Tabular>
                        </Link>
                    </Field>
                    <Field label={copy.keyId}>
                        <Tabular>{resource.signature.kid}</Tabular>
                    </Field>
                </dl>
                <dl className="grid gap-2">
                    <Field label={copy.digest}>
                        <Tabular className="font-mono text-xs">
                            {document.report.digest_algorithm}:{document.report.digest}
                        </Tabular>
                    </Field>
                </dl>
                <section className="space-y-2">
                    <Heading className="text-muted-foreground">{copy.configuration}</Heading>
                    <dl className="grid gap-2 md:grid-cols-2">
                        {Object.entries(document.configuration).map(([name, value]) => (
                            <Field key={name} label={name}>
                                <Tabular className="font-mono text-xs">
                                    {displayValue(value)}
                                </Tabular>
                            </Field>
                        ))}
                    </dl>
                </section>
            </CardContent>
        </Card>
    )
}

function displayValue(value: unknown): React.ReactNode {
    if (value !== null && typeof value === "object" && !Array.isArray(value)) {
        return (
            <span className="block space-y-0.5">
                {Object.entries(value).map(([name, entry]) => (
                    <span key={name} className="block">
                        {name}: {String(entry)}
                    </span>
                ))}
            </span>
        )
    }
    return typeof value === "string" ? value : JSON.stringify(value)
}
