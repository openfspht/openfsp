import js from "@eslint/js"
import globals from "globals"
import tseslint from "typescript-eslint"
import { defineConfig, globalIgnores } from "eslint/config"

export default defineConfig([
    globalIgnores(["dist"]),
    {
        files: ["**/*.ts"],
        extends: [js.configs.recommended, tseslint.configs.recommended],
        languageOptions: {
            globals: globals.node,
        },
        rules: {
            curly: ["error", "all"],
            "@typescript-eslint/no-empty-object-type": [
                "error",
                { allowInterfaces: "with-single-extends" },
            ],
            "@typescript-eslint/no-unsafe-declaration-merging": "off",
        },
    },
    {
        files: ["test/**/*.ts"],
        rules: {
            "@typescript-eslint/no-explicit-any": "off",
        },
    },
])
