import { useQuery } from "@tanstack/react-query"
import type { SuiteLocale } from "@workspace/types"

import { queryKeys, validatorApi } from "@/api/validator-api"

import { useI18n } from "./i18n-provider"
import { translateReason } from "./suite-reasons"

export function useSuiteText() {
    const { locale } = useI18n()
    const suite = useQuery({ queryKey: queryKeys.suite, queryFn: validatorApi.suite })
    const texts: SuiteLocale | undefined = suite.data?.locales?.[locale]
    const french = locale === "fr"
    return {
        scenario: (id: string, english: string) => texts?.scenarios[id]?.title ?? english,
        action: (id: string, english: string) => texts?.scenarios[id]?.action ?? english,
        limit: (reference: string, english: string) => texts?.limits[reference] ?? english,
        reason: (english: string) => (french ? translateReason(english) : english),
    }
}
