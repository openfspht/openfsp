# Changelog

## [0.5.0](https://github.com/openfspht/openfsp/compare/sdk-typescript-v0.4.0...sdk-typescript-v0.5.0) (2026-11-21)


### ⚠ BREAKING CHANGES

* read statements with forDay and between in the TypeScript SDK, and refuse an empty cursor in the Kotlin SDK
* drop synchronize, a read establishes the most recent state
* carry the operator fee from the payment to the statement
* reduce the statement to a list of successful transactions

### Features

* bring the TypeScript SDK in line with the Kotlin SDK ([ad9133a](https://github.com/openfspht/openfsp/commit/ad9133a7ecd7a4ba4794386e95c63d4e65a51bec))
* carry the operator fee from the payment to the statement ([205d6c9](https://github.com/openfspht/openfsp/commit/205d6c9163ef7c01045732ed0c8e5fe3b2fc2c4c))
* drop synchronize, a read establishes the most recent state ([bcc69dd](https://github.com/openfspht/openfsp/commit/bcc69ddc22c407366b3f88977593aecff98ded98))
* expose the amount bounds and the instance of a problem in ApiError and ApiException ([e3c9804](https://github.com/openfspht/openfsp/commit/e3c980460881892df446802b4e18144a2a1bfa36))
* map conformance_endpoint in the ServerConfiguration of both SDKs ([35ce099](https://github.com/openfspht/openfsp/commit/35ce0996f23f85465d1b70a9f2e21d61f2df0ca5))
* raise a dedicated error for an unknown payment status in both SDKs ([3613bba](https://github.com/openfspht/openfsp/commit/3613bba287525061c838ed73f28e5c1439a5f3c8))
* read statements with forDay and between in the TypeScript SDK, and refuse an empty cursor in the Kotlin SDK ([104b4ea](https://github.com/openfspht/openfsp/commit/104b4ea9c51645cf149a18bf767c1375c221f282))
* reduce the statement to a list of successful transactions ([165ec22](https://github.com/openfspht/openfsp/commit/165ec2272788aebb5fb18b9d8d462e7b0c770bd6))
* report a verified webhook of an unknown event type apart from a signature failure and export payment status helpers ([6811087](https://github.com/openfspht/openfsp/commit/6811087d39b55295736bbe170fc376e7e2e691b2))
* verify ecdsa-p256-sha256 webhook signatures with ES256 keys in the protocol and both SDKs ([7d3c266](https://github.com/openfspht/openfsp/commit/7d3c2669a135842b9dcf1a919cda1ca4b07ddf56))


### Bug Fixes

* build the integration client only when the integration test runs ([b741e99](https://github.com/openfspht/openfsp/commit/b741e99f5df5a8fdc9fe0b2d18a7f9c3c60f1589))
* check TypeScript webhook signatures in the order of the specification and reserve the nonce last ([1429a92](https://github.com/openfspht/openfsp/commit/1429a92cb828b2d8d9f017bbc59cbe80d5c6dd4f))
* read an unknown failure reason as unspecified in the SDKs ([9013361](https://github.com/openfspht/openfsp/commit/9013361706aaf041a25f425107f39c165c656dbe))
* report a malformed capabilities document or collection as ApiError in the TypeScript SDK ([18d860e](https://github.com/openfspht/openfsp/commit/18d860eae7c988affce0dc476a8dd325974782b9))
* treat a redirect without URL or an unknown next action as an unexpected response in the TypeScript SDK ([f13a092](https://github.com/openfspht/openfsp/commit/f13a092ed241e184354d65accd53c566bddb442b))
