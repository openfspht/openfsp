import { resolve } from "node:path"
import tailwindcss from "@tailwindcss/vite"
import react from "@vitejs/plugin-react"
import { defineConfig } from "vite"

const validator = process.env.VALIDATOR_URL ?? "http://127.0.0.1:8095"

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      "@": resolve(import.meta.dirname, "./src"),
    },
  },
  server: {
    port: Number(process.env.VALIDATOR_UI_PORT ?? 5176),
    strictPort: true,
    proxy: {
      "/api": { target: validator },
      "/.well-known/openfsp-validator": { target: validator },
    },
  },
})
