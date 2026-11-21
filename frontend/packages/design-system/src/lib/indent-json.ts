const WHITESPACE = /\s/

export function indentJson(text: string, width = 2): string {
    JSON.parse(text)
    let output = ""
    let depth = 0
    let inString = false
    let escaped = false
    const newline = () => `\n${" ".repeat(depth * width)}`
    for (let index = 0; index < text.length; index++) {
        const char = text[index]
        if (inString) {
            output += char
            if (escaped) {
                escaped = false
            } else if (char === "\\") {
                escaped = true
            } else if (char === '"') {
                inString = false
            }
            continue
        }
        if (WHITESPACE.test(char)) {
            continue
        }
        if (char === '"') {
            inString = true
            output += char
        } else if (char === "{" || char === "[") {
            let next = index + 1
            while (next < text.length && WHITESPACE.test(text[next])) {
                next++
            }
            if (text[next] === (char === "{" ? "}" : "]")) {
                output += char + text[next]
                index = next
            } else {
                depth++
                output += char + newline()
            }
        } else if (char === "}" || char === "]") {
            depth--
            output += newline() + char
        } else if (char === ",") {
            output += `,${newline()}`
        } else if (char === ":") {
            output += ": "
        } else {
            output += char
        }
    }
    return output
}
