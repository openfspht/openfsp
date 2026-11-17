import { createContext, useContext } from "react"

import { en, type DesignSystemCopy } from "./en"

const CopyContext = createContext<DesignSystemCopy>(en)

export function CopyProvider({
    copy,
    children,
}: {
    copy: DesignSystemCopy
    children: React.ReactNode
}) {
    return <CopyContext.Provider value={copy}>{children}</CopyContext.Provider>
}

export const useCopy = () => useContext(CopyContext)
