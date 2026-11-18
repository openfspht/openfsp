export type BareItem =
    | { readonly type: "integer"; readonly value: number }
    | { readonly type: "decimal"; readonly value: number }
    | { readonly type: "string"; readonly value: string }
    | { readonly type: "token"; readonly value: string }
    | { readonly type: "bytes"; readonly value: Uint8Array<ArrayBuffer> }
    | { readonly type: "boolean"; readonly value: boolean }

export type Parameters = ReadonlyMap<string, BareItem>

export interface Item {
    readonly kind: "item"
    readonly value: BareItem
    readonly parameters: Parameters
}

export interface InnerList {
    readonly kind: "list"
    readonly items: readonly Item[]
    readonly parameters: Parameters
}

export type Member = Item | InnerList

const MAX_INTEGER_DIGITS = 15
const MAX_DECIMAL_DIGITS = 16
const MAX_DECIMAL_INTEGER_DIGITS = 12
const MAX_FRACTION_DIGITS = 3
const KEY_START = /^[a-z*]$/
const KEY_CHARACTER = /^[a-z0-9_\-.*]$/
const TOKEN_START = /^[A-Za-z*]$/
const TOKEN_CHARACTER = /^[A-Za-z0-9!#$%&'*+\-.^_`|~:/]$/
const BASE64_CHARACTER = /^[A-Za-z0-9+/=]$/
const DIGIT = /^[0-9]$/

class MalformedField extends Error {}

export function parseDictionary(input: string): Map<string, Member> | undefined {
    try {
        return new Parser(input).dictionary()
    } catch (failure) {
        if (failure instanceof MalformedField) {
            return undefined
        }
        throw failure
    }
}

export function serializeInnerList(list: InnerList): string {
    const items = list.items.map(
        (item) => serializeBare(item.value) + serializeParameters(item.parameters)
    )
    return `(${items.join(" ")})${serializeParameters(list.parameters)}`
}

function serializeParameters(parameters: Parameters): string {
    let serialized = ""
    for (const [key, value] of parameters) {
        serialized +=
            value.type === "boolean" && value.value ? `;${key}` : `;${key}=${serializeBare(value)}`
    }
    return serialized
}

function serializeBare(item: BareItem): string {
    switch (item.type) {
        case "integer":
            return String(item.value)
        case "decimal": {
            const rounded = String(Math.round(item.value * 1000) / 1000)
            return rounded.includes(".") ? rounded : `${rounded}.0`
        }
        case "string":
            return `"${item.value.replace(/[\\"]/g, (character) => `\\${character}`)}"`
        case "token":
            return item.value
        case "bytes":
            return `:${encodeBase64(item.value)}:`
        case "boolean":
            return item.value ? "?1" : "?0"
    }
}

export function encodeBase64(bytes: Uint8Array): string {
    let binary = ""
    for (const byte of bytes) {
        binary += String.fromCharCode(byte)
    }
    return btoa(binary)
}

class Parser {
    private position = 0
    private readonly input: string

    constructor(input: string) {
        this.input = input
    }

    dictionary(): Map<string, Member> {
        const members = new Map<string, Member>()
        this.skip(" ")
        while (!this.atEnd()) {
            const key = this.key()
            if (this.peek() === "=") {
                this.position++
                members.set(key, this.member())
            } else {
                members.set(key, {
                    kind: "item",
                    value: { type: "boolean", value: true },
                    parameters: this.parameters(),
                })
            }
            this.skipWhitespace()
            if (this.atEnd()) {
                return members
            }
            this.expect(",")
            this.skipWhitespace()
            if (this.atEnd()) {
                this.fail()
            }
        }
        return members
    }

    private member(): Member {
        if (this.peek() === "(") {
            return this.innerList()
        }
        return { kind: "item", value: this.bareItem(), parameters: this.parameters() }
    }

    private innerList(): InnerList {
        this.expect("(")
        const items: Item[] = []
        while (!this.atEnd()) {
            this.skip(" ")
            if (this.peek() === ")") {
                this.position++
                return { kind: "list", items, parameters: this.parameters() }
            }
            items.push({ kind: "item", value: this.bareItem(), parameters: this.parameters() })
            if (this.peek() !== " " && this.peek() !== ")") {
                this.fail()
            }
        }
        return this.fail()
    }

    private parameters(): Map<string, BareItem> {
        const parameters = new Map<string, BareItem>()
        while (this.peek() === ";") {
            this.position++
            this.skip(" ")
            const key = this.key()
            if (this.peek() === "=") {
                this.position++
                parameters.set(key, this.bareItem())
            } else {
                parameters.set(key, { type: "boolean", value: true })
            }
        }
        return parameters
    }

    private key(): string {
        if (!KEY_START.test(this.peek())) {
            this.fail()
        }
        const start = this.position
        while (KEY_CHARACTER.test(this.peek())) {
            this.position++
        }
        return this.input.slice(start, this.position)
    }

    private bareItem(): BareItem {
        const next = this.peek()
        if (next === "-" || DIGIT.test(next)) {
            return this.number()
        }
        if (next === '"') {
            return this.string()
        }
        if (next === ":") {
            return this.bytes()
        }
        if (next === "?") {
            return this.boolean()
        }
        if (TOKEN_START.test(next)) {
            return this.token()
        }
        return this.fail()
    }

    private number(): BareItem {
        const start = this.position
        const sign = this.peek() === "-" ? 1 : 0
        this.position += sign
        if (!DIGIT.test(this.peek())) {
            this.fail()
        }
        let decimal = false
        for (;;) {
            const next = this.peek()
            if (DIGIT.test(next)) {
                this.position++
            } else if (next === "." && !decimal) {
                if (this.position - start - sign > MAX_DECIMAL_INTEGER_DIGITS) {
                    this.fail()
                }
                decimal = true
                this.position++
            } else {
                break
            }
            const length = this.position - start - sign
            if (length > (decimal ? MAX_DECIMAL_DIGITS : MAX_INTEGER_DIGITS)) {
                this.fail()
            }
        }
        const text = this.input.slice(start, this.position)
        if (!decimal) {
            return { type: "integer", value: Number(text) }
        }
        const fraction = text.slice(text.indexOf(".") + 1)
        if (fraction.length === 0 || fraction.length > MAX_FRACTION_DIGITS) {
            this.fail()
        }
        return { type: "decimal", value: Number(text) }
    }

    private string(): BareItem {
        this.expect('"')
        let value = ""
        for (;;) {
            if (this.atEnd()) {
                this.fail()
            }
            const next = this.input[this.position++]!
            if (next === "\\") {
                const escaped = this.peek()
                if (escaped !== '"' && escaped !== "\\") {
                    this.fail()
                }
                value += escaped
                this.position++
            } else if (next === '"') {
                return { type: "string", value }
            } else if (next < " " || next > "~") {
                this.fail()
            } else {
                value += next
            }
        }
    }

    private token(): BareItem {
        const start = this.position++
        while (TOKEN_CHARACTER.test(this.peek())) {
            this.position++
        }
        return { type: "token", value: this.input.slice(start, this.position) }
    }

    private bytes(): BareItem {
        this.expect(":")
        const start = this.position
        while (BASE64_CHARACTER.test(this.peek())) {
            this.position++
        }
        const encoded = this.input.slice(start, this.position)
        this.expect(":")
        if (!/^[A-Za-z0-9+/]*={0,2}$/.test(encoded)) {
            this.fail()
        }
        try {
            return {
                type: "bytes",
                value: Uint8Array.from(atob(encoded), (character) => character.charCodeAt(0)),
            }
        } catch {
            return this.fail()
        }
    }

    private boolean(): BareItem {
        this.expect("?")
        const next = this.peek()
        if (next !== "0" && next !== "1") {
            this.fail()
        }
        this.position++
        return { type: "boolean", value: next === "1" }
    }

    private peek(): string {
        return this.input[this.position] ?? ""
    }

    private atEnd(): boolean {
        return this.position >= this.input.length
    }

    private expect(character: string): void {
        if (this.peek() !== character) {
            this.fail()
        }
        this.position++
    }

    private skip(character: string): void {
        while (this.peek() === character) {
            this.position++
        }
    }

    private skipWhitespace(): void {
        while (this.peek() === " " || this.peek() === "\t") {
            this.position++
        }
    }

    private fail(): never {
        throw new MalformedField()
    }
}
