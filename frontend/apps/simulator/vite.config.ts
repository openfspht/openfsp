import { resolve } from "node:path"
import tailwindcss from "@tailwindcss/vite"
import react from "@vitejs/plugin-react"
import { defineConfig } from "vite"

const simulator = "http://127.0.0.1:8090"
const interfaceKey = "ofsp_test_MockOpenFspKeyPublishedForTheSimulator"

export default defineConfig({
    plugins: [react(), tailwindcss()],
    resolve: {
        alias: {
            "@": resolve(import.meta.dirname, "./src"),
        },
    },
    server: {
        port: 5175,
        strictPort: true,
        proxy: {
            "/simulator": {
                target: simulator,
                rewrite: (path) => path.replace(/^\/simulator/, ""),
                headers: { Authorization: `Bearer ${interfaceKey}` },
            },
        },
    },
})
