import {
    ConsoleNavBar,
    ConsoleShell,
    LanguageSwitch,
    navLinkClass,
    ThemeSwitch,
} from "@workspace/design-system"
import { NavLink, Outlet } from "react-router"

import { useI18n } from "@/i18n/i18n-provider"

export function ValidatorFrame() {
    const { t } = useI18n()
    return (
        <ConsoleShell
            navigation={
                <ConsoleNavBar
                    product={t.product}
                    environment="test"
                    tools={
                        <>
                            <LanguageSwitch />
                            <ThemeSwitch />
                        </>
                    }
                    navigation={
                        <NavLink to="/" end className={({ isActive }) => navLinkClass(isActive)}>
                            {t.nav.run}
                        </NavLink>
                    }
                />
            }
        >
            <Outlet />
        </ConsoleShell>
    )
}
