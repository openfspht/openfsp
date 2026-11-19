import { readdirSync, readFileSync, statSync, writeFileSync } from "node:fs"
import { join } from "node:path"
import { brotliCompressSync, constants, gzipSync } from "node:zlib"

const COMPRESSIBLE = /\.(html|js|css|svg|json|txt|map)$/
const MIN_BYTES = 1024

function files(directory: string): string[] {
    return readdirSync(directory).flatMap((name) => {
        const path = join(directory, name)
        return statSync(path).isDirectory() ? files(path) : [path]
    })
}

const root = process.argv[2]
if (!root) {
    console.error("Usage: bun scripts/precompress.ts <dist directory>")
    process.exit(64)
}

for (const path of files(root).filter((file) => COMPRESSIBLE.test(file))) {
    const content = readFileSync(path)
    if (content.length < MIN_BYTES && !path.endsWith(".html")) {
        continue
    }
    writeFileSync(`${path}.gz`, gzipSync(content, { level: 9 }))
    writeFileSync(
        `${path}.br`,
        brotliCompressSync(content, { params: { [constants.BROTLI_PARAM_QUALITY]: 11 } })
    )
}
