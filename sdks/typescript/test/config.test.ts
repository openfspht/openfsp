import { describe, expect, test } from "bun:test"
import { ConfigurationError, OpenFspConfig } from "../src/index"

describe("OpenFspConfig", () => {
  test("normalizes the base URL under its path prefix", () => {
    expect(OpenFspConfig.from({ baseUrl: "https://gateway.example/openfsp", apiKey: "k" }).baseUrl.href).toBe("https://gateway.example/openfsp/")
  })

  test("refuses plain http outside a loopback host unless allowed", () => {
    expect(() => OpenFspConfig.from({ baseUrl: "http://gateway.example", apiKey: "k" })).toThrow(ConfigurationError)
    expect(OpenFspConfig.from({ baseUrl: "http://127.0.0.1:8080", apiKey: "k" }).baseUrl.protocol).toBe("http:")
    expect(OpenFspConfig.from({ baseUrl: "http://gateway:8080", apiKey: "k", allowPlainHttp: true }).baseUrl.host).toBe("gateway:8080")
  })

  test.each([
    [{ baseUrl: "gateway.example", apiKey: "k" }, "absolute URL"],
    [{ baseUrl: "https://user:secret@gateway.example", apiKey: "k" }, "credentials"],
    [{ baseUrl: "ftp://gateway.example", apiKey: "k" }, "https"],
    [{ baseUrl: "https://gateway.example", apiKey: "a key" }, "apiKey"],
    [{ baseUrl: "https://gateway.example", apiKey: "k", timeoutMs: 0 }, "timeoutMs"],
  ])("rejects %o", (options, message) => {
    expect(() => OpenFspConfig.from(options)).toThrow(message)
  })

  test("reads the environment", () => {
    const config = OpenFspConfig.fromEnv({ OPENFSP_BASE_URL: "http://gateway:8080", OPENFSP_API_KEY: "ofsp_test_key", OPENFSP_ALLOW_PLAIN_HTTP: "true", OPENFSP_TIMEOUT_MS: "5000", OPENFSP_WEBHOOK_URL: "https://shop.example/webhooks" })
    expect(config.timeoutMs).toBe(5000)
    expect(config.webhookUrl).toBe("https://shop.example/webhooks")
    expect(() => OpenFspConfig.fromEnv({})).toThrow("OPENFSP_BASE_URL is required")
  })

  test("never shows the api key", () => {
    const config = OpenFspConfig.from({ baseUrl: "https://gateway.example", apiKey: "ofsp_live_secret" })
    expect(config.apiKey).toBe("ofsp_live_secret")
    expect(JSON.stringify(config)).not.toContain("secret")
    expect(Object.keys(config)).not.toContain("apiKey")
  })
})
