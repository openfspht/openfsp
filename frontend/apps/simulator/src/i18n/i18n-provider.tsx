import { createI18n } from "@workspace/design-system"

import { en } from "./en"
import { fr } from "./fr"

export const { I18nProvider, useI18n } = createI18n("openfsp.simulator.locale", { en, fr })
