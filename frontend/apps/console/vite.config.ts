import { resolve } from "node:path"
import tailwindcss from "@tailwindcss/vite"
import react from "@vitejs/plugin-react"
import { defineConfig } from "vite"

const gateway = "http://127.0.0.1:8080"

export default defineConfig({
    plugins: [react(), tailwindcss()],
    resolve: {
        alias: {
            "@": resolve(import.meta.dirname, "./src"),
        },
    },
    server: {
        proxy: {
            "/admin": { target: gateway },
        },
    },
})
