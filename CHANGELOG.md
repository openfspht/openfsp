# Changelog

## [0.7.0](https://github.com/openfspht/openfsp/compare/v0.6.0...v0.7.0) (2026-11-24)


### Features

* accept CIDR blocks in the gateway trusted proxies ([da5ca57](https://github.com/openfspht/openfsp/commit/da5ca57ad232f99f600ce318d0a2548ef3d6825e))


### Bug Fixes

* explain when the manual run inputs apply and describe them for the web form ([8ddff59](https://github.com/openfspht/openfsp/commit/8ddff59ba6d613436a007d484d0a43cc27c6d1ed))

## [0.6.0](https://github.com/openfspht/openfsp/compare/v0.5.0...v0.6.0) (2026-11-21)


### Features

* count the Core scenario verdicts in attestations ([e82c64a](https://github.com/openfspht/openfsp/commit/e82c64a214f0d6af93c4ef3aa176a6976ff63ce7))
* give each attestation a short share link served from a dedicated host ([e92cec7](https://github.com/openfspht/openfsp/commit/e92cec7b12fb76029ec98dd6c1f7bc8ae589705e))
* lay out validator reports and attestations in two columns with filters and the Core tally ([9bffaaa](https://github.com/openfspht/openfsp/commit/9bffaaaadba997c1a68244136c8a6bf870261649))
* render the attestation PDF on one branded page in the reader's language ([36637e7](https://github.com/openfspht/openfsp/commit/36637e7fec25d71a1adf5112113beefb8b68d508))


### Bug Fixes

* mask only secret conformance inputs so reports keep the bounded provider readable ([079402c](https://github.com/openfspht/openfsp/commit/079402c15dd50fc19a5421f0bd596654395c9b08))
* send an https return_url in the pending state scenario so production servers accept it ([2d6639e](https://github.com/openfspht/openfsp/commit/2d6639ea1ad4a0c3f399bc6dee937406a4d636d5))
* show an incomplete Core as incomplete on the attestation badge ([f0db565](https://github.com/openfspht/openfsp/commit/f0db565e6b1bb2937fe203646a9c3da87c9c2a44))

## [0.5.0](https://github.com/openfspht/openfsp/compare/v0.4.0...v0.5.0) (2026-11-21)


### ⚠ BREAKING CHANGES

* ship one image per service that serves its own interface instead of separate interface images
* type the locale, next actions and locales of the Kotlin SDK as enums
* require the control endpoint with a manual key and drop the client target and the assisted exit of the validator
* start the validator outside development unless a profile chooses it
* read statements with forDay and between in the TypeScript SDK, and refuse an empty cursor in the Kotlin SDK
* answer capability-not-supported for statements and callback_url the provider does not perform itself
* start the gateway with production defaults and refuse the published demo admin token
* derive every gateway key from a single OPENFSP_APP_KEY and keep no key in the repository
* isolate gateway payments, keys, statements and webhooks by merchant account
* drop synchronize, a read establishes the most recent state
* fail outcome scenarios an implementation cannot control
* drive payment outcomes through the control interface of the implementation
* carry the operator fee from the payment to the statement
* reduce the statement to a list of successful transactions

### Features

* add a demo profile that accepts the published admin token and API key ([49548fb](https://github.com/openfspht/openfsp/commit/49548fb059ed44256f7d05d4a9dd8b321621e156))
* add serve, hash and validate commands to the images ([b470be1](https://github.com/openfspht/openfsp/commit/b470be18ffd940bfc1fb3a16bf2f9862531faa4f))
* ask for the control endpoint with a manual key in the validator form ([8cb22e6](https://github.com/openfspht/openfsp/commit/8cb22e645ebe28a4a236db6f6b3422fce380ebb8))
* bind sealed provider credentials to their revision and reseal the stored ones ([ea74c93](https://github.com/openfspht/openfsp/commit/ea74c93b7f9a4399b2e25488d3891eef9f38930c))
* call the admin API on the console origin ([f8f860c](https://github.com/openfspht/openfsp/commit/f8f860c5d91756c7d9bd34d540a5e92299c4c936))
* carry the operator fee from the payment to the statement ([205d6c9](https://github.com/openfspht/openfsp/commit/205d6c9163ef7c01045732ed0c8e5fe3b2fc2c4c))
* choose platform or own provider credentials per account explicitly, never falling back ([2510bc2](https://github.com/openfspht/openfsp/commit/2510bc21ebedbb7a02ced513f7c9af99bcd810fd))
* declare and enforce amount bounds for mock_openfsp ([9c9fa19](https://github.com/openfspht/openfsp/commit/9c9fa19947df7379b8d8fe86a5d107b37a9647c0))
* derive every gateway key from a single OPENFSP_APP_KEY and keep no key in the repository ([6cf241c](https://github.com/openfspht/openfsp/commit/6cf241c1e52ddaa417129f3a27af44bfc5d80333))
* drive payment outcomes through the control interface of the implementation ([9c0686b](https://github.com/openfspht/openfsp/commit/9c0686b6ac5116bee277a340a282b53887abc149))
* drop synchronize, a read establishes the most recent state ([bcc69dd](https://github.com/openfspht/openfsp/commit/bcc69ddc22c407366b3f88977593aecff98ded98))
* emit signed webhook events for mock_openfsp conformance runs ([7be4fa6](https://github.com/openfspht/openfsp/commit/7be4fa6a2ed2d353e4188a7a1e72dbee64f96aa1))
* expose the amount bounds and the instance of a problem in ApiError and ApiException ([e3c9804](https://github.com/openfspht/openfsp/commit/e3c980460881892df446802b4e18144a2a1bfa36))
* fail outcome scenarios an implementation cannot control ([16e449b](https://github.com/openfspht/openfsp/commit/16e449b707dd983152a1d56004296d2b358117e6))
* give the interfaces the design system colours, neutral dark theme and JetBrains Mono ([635c1e6](https://github.com/openfspht/openfsp/commit/635c1e618412411f9d4c34c93304e86c0c123a72))
* isolate gateway payments, keys, statements and webhooks by merchant account ([b92aa64](https://github.com/openfspht/openfsp/commit/b92aa642e67fc0116ad0fa01e62d38c50b5afd28))
* isolate mock_openfsp conformance runs in their own merchant account ([ddf53b9](https://github.com/openfspht/openfsp/commit/ddf53b940c0fb8b8c5aded6e80a6d5f6cf8b63f7))
* keep provider credentials as revisions that pending payments keep using, with revocation ([5c7e4b3](https://github.com/openfspht/openfsp/commit/5c7e4b36476d13cd377df50e5fb84bf7a75d23d0))
* let a caller of the Kotlin SDK handle lost answers and unknown statuses itself ([8fb12e9](https://github.com/openfspht/openfsp/commit/8fb12e913fa60d8afeee07b4acd1149d941fd336))
* let a merchant account use its own MonCash and NatCash credentials, sealed at rest ([452280c](https://github.com/openfspht/openfsp/commit/452280c8f822f6630c276b71a432a3926e993955))
* let the gateway play the outcomes a conformance run chooses ([74b3459](https://github.com/openfspht/openfsp/commit/74b345908b2be951852b5ff7e1634cbcad29f47a))
* let the simulator interface use the control interface without exposing its key ([abb9319](https://github.com/openfspht/openfsp/commit/abb93195271428cffabc4ecf3f1e4e16d3928094))
* let the validator command line ask the target for its test keys ([4d12b58](https://github.com/openfspht/openfsp/commit/4d12b587b489d3202e83d3cab8405777ea3273f7))
* list credential revisions and revoke them from the console ([2630671](https://github.com/openfspht/openfsp/commit/2630671b2306e04990512db4d77e776f004ec37d))
* manage merchant accounts and filter by account in the console ([29aa743](https://github.com/openfspht/openfsp/commit/29aa7436fc9d0df29aea637adda9c6b4a07adaca))
* manage the payment service credentials of a merchant account in the console ([2eeb0fd](https://github.com/openfspht/openfsp/commit/2eeb0fd722432c592190a68da62ac1a7968cc2e1))
* map conformance_endpoint in the ServerConfiguration of both SDKs ([35ce099](https://github.com/openfspht/openfsp/commit/35ce0996f23f85465d1b70a9f2e21d61f2df0ca5))
* own the idempotency request fingerprint rule in the protocol with shared vectors ([e84263d](https://github.com/openfspht/openfsp/commit/e84263d7ef4b66769c8a7aa707ed8db36c16b989))
* print only the hash from the gateway hash command and refuse the demo token ([346582e](https://github.com/openfspht/openfsp/commit/346582e77a03f23134f06bcc5677c3eaa7d2ef4b))
* raise a dedicated error for an unknown payment status in both SDKs ([3613bba](https://github.com/openfspht/openfsp/commit/3613bba287525061c838ed73f28e5c1439a5f3c8))
* read ECDSA P-256 webhook keys in the validator and describe their members in the discovery schema ([770324a](https://github.com/openfspht/openfsp/commit/770324a1e39c5a3ca1b5ca3252e77f2e46f162e3))
* read statements and follow the announced capabilities lifetime in the Kotlin SDK ([ed01106](https://github.com/openfspht/openfsp/commit/ed01106f76e6e3f1dff2d695368c30ff7e6b2fa8))
* read statements with forDay and between in the TypeScript SDK, and refuse an empty cursor in the Kotlin SDK ([104b4ea](https://github.com/openfspht/openfsp/commit/104b4ea9c51645cf149a18bf767c1375c221f282))
* reduce the statement to a list of successful transactions ([165ec22](https://github.com/openfspht/openfsp/commit/165ec2272788aebb5fb18b9d8d462e7b0c770bd6))
* report a verified webhook of an unknown event type apart from a signature failure and export payment status helpers ([6811087](https://github.com/openfspht/openfsp/commit/6811087d39b55295736bbe170fc376e7e2e691b2))
* require the control endpoint with a manual key and drop the client target and the assisted exit of the validator ([1cf61dd](https://github.com/openfspht/openfsp/commit/1cf61dd41dca82b5b882ab8cd67554c3ae713225))
* revoke attestation keys by kid and erase stored private keys outside development mode ([a39f749](https://github.com/openfspht/openfsp/commit/a39f749d26fe96caf370131e9a0be60420516ba1))
* route the demo services through traefik on sslip.io names ([1c6f1cc](https://github.com/openfspht/openfsp/commit/1c6f1cca855f46151a3e36a2fc73ede141512b05))
* run the validator CLI on its bundled suite when no conformance directory is given ([f92a60a](https://github.com/openfspht/openfsp/commit/f92a60a9965c5c899ac77c18dacf8c3e795bd381))
* send the MonCash webhook and return the NatCash payer with a signed result ([2c3007c](https://github.com/openfspht/openfsp/commit/2c3007cacfae63c6bbbd7ded83e401577a0bee3d))
* serve the bundled web interface from the gateway, simulator and validator applications ([3de1275](https://github.com/openfspht/openfsp/commit/3de127529af8163eff67d0f017663b9b34f95b7c))
* serve the console and the admin API only on the configured admin host ([e470543](https://github.com/openfspht/openfsp/commit/e4705439f1e4ada1ea92d786050e730ac548391a))
* settle MonCash payments on their webhook and return NatCash payers to the merchant ([fb4e156](https://github.com/openfspht/openfsp/commit/fb4e156079c178545ea2d58f934d63aff1233ad9))
* show demo keys as demo only in the console outside the demo ([e1b854e](https://github.com/openfspht/openfsp/commit/e1b854ee0dcbf1e4be794dcf7ea5f33a1a5eed2a))
* show the conformance suite in French when the validator interface is in French ([fbc4a4d](https://github.com/openfspht/openfsp/commit/fbc4a4dd849c37618f3966cd137935f532b90b8d))
* show the credentials mode per provider and switch an account to the platform from the console ([6738ba2](https://github.com/openfspht/openfsp/commit/6738ba259fd4afdbdaa3463b908e02f96b9e842e))
* state a reason code and a reason for every not applicable scenario and profile ([a3b6645](https://github.com/openfspht/openfsp/commit/a3b6645fe6811a50864724597132d22367197cb0))
* stripe the rows of the validation results table ([faa5b87](https://github.com/openfspht/openfsp/commit/faa5b8758d6db4e035b39eebe82052559a9113d7))
* take the base URL first in validate and write the JSON report only on request ([a1791ec](https://github.com/openfspht/openfsp/commit/a1791ecf97410388c8bafcf7662b0fa9a185acd0))
* type the locale, next actions and locales of the Kotlin SDK as enums ([d9de140](https://github.com/openfspht/openfsp/commit/d9de140db555307192797adb2f709f849b4364f3))
* verify ecdsa-p256-sha256 webhook signatures with ES256 keys in the protocol and both SDKs ([7d3c266](https://github.com/openfspht/openfsp/commit/7d3c2669a135842b9dcf1a919cda1ca4b07ddf56))


### Bug Fixes

* accept the published demo token and key only under the demo profile, and revoke the key outside it ([f93ec9c](https://github.com/openfspht/openfsp/commit/f93ec9caefda8b0c5b63bcdb1494f5a0c64f7ff5))
* acknowledge a verified provider event of an unknown type instead of rejecting the callback ([adf082f](https://github.com/openfspht/openfsp/commit/adf082f192e941e018729ebd86d289447b18d639))
* announce the loopback address for a webhook receiver listening on every address ([941f087](https://github.com/openfspht/openfsp/commit/941f08700671a413674588663874fb5828785223))
* answer capability-not-supported for a simulated capability the operator lacks and find a payment by reference without lookup ([c9e416c](https://github.com/openfspht/openfsp/commit/c9e416c85241e3d375fbca1d932788618e475227))
* answer capability-not-supported for statements and callback_url the provider does not perform itself ([09367f7](https://github.com/openfspht/openfsp/commit/09367f79eff31c88961f7bfd5eacc7779d76e860))
* answer provider-unavailable with a clear detail for account credentials sealed under another application key ([ef2c030](https://github.com/openfspht/openfsp/commit/ef2c030b909387edb83dfa919d6165e480fcc8b8))
* answer reference-conflict for a creation resent after the idempotency retention ([e7ff696](https://github.com/openfspht/openfsp/commit/e7ff696eb5feec13f3b00dcf29f8a7cae0fe448b))
* apply the sandbox override after .env and skip indented comments ([e0f6f8c](https://github.com/openfspht/openfsp/commit/e0f6f8c84f1c1d94c089d7bdecc69dc59d802e3c))
* bind the demonstration services to the loopback and keep PostgreSQL private in a named volume ([64f65d5](https://github.com/openfspht/openfsp/commit/64f65d5ca83436c36afbed94883aa4a42188675a))
* bind the development gateway to loopback, skip conformance in the sandbox and read .env literally ([e750c29](https://github.com/openfspht/openfsp/commit/e750c29fc7b2822fd47ef676f3e8acd008b98aae))
* book statement entries at the time the provider completed the payment ([4b995bf](https://github.com/openfspht/openfsp/commit/4b995bfe71e5e0fb426bcca33acc1817e1ed038b))
* bound each validator response in size and time and each run in total duration ([575338e](https://github.com/openfspht/openfsp/commit/575338e5d36db4d8a580c27fa342f4431cacde9a))
* bound the webhooks a validator run receives and the prepared runs it holds, and report refused deliveries ([e847073](https://github.com/openfspht/openfsp/commit/e8470732d2e01d2ef0abb20750201323dcc55664))
* cap and expire simulator conformance preparations and find a run key without scanning every run ([3425d34](https://github.com/openfspht/openfsp/commit/3425d3488dbbfb94f2ac53f5cce72c3306427606))
* cap concurrent admin sign-in verifications ([a847a2f](https://github.com/openfspht/openfsp/commit/a847a2f9daf17cd774825b5a5c53351c48a31939))
* claim pending payments for reconciliation with SKIP LOCKED and back off after each attempt ([803ec9c](https://github.com/openfspht/openfsp/commit/803ec9c263321e76e2ce56b1c6d2a79e1d4dc3a9))
* count the events delivered to the webhook_url of a run in callbacks_sent ([c28482a](https://github.com/openfspht/openfsp/commit/c28482a3c399d9bd433b6e4a70b54070812ee9b4))
* decide admin, key and rate limit access on the canonical path and refuse ambiguous paths ([b562fe3](https://github.com/openfspht/openfsp/commit/b562fe3c50d53b9d0f7210c734a612050be8be7a))
* default the validator service and its attestations to validator.openfsp.org ([8688924](https://github.com/openfspht/openfsp/commit/8688924aca04d8c66f607d8cd453b1aab0a97195))
* detect a replayed webhook nonce across a run and require a fresh nonce and created on a retried delivery ([7cb54bc](https://github.com/openfspht/openfsp/commit/7cb54bcd4c248536a42014ee38cf90515073e91e))
* drive the simulated control interface with a preparation key only, refuse other keys with 403 and settled payments with 409 ([2a7bdd4](https://github.com/openfspht/openfsp/commit/2a7bdd4dfffb1438d71033a7aefd714ed35d4135))
* drop audit bodies that are not parseable json or a form when they name a secret ([fa7cc78](https://github.com/openfspht/openfsp/commit/fa7cc78ed65ef66d59d4ae2b13616a2a7382afcc))
* drop the Tailwind source pointing to a missing components folder ([a0c81cd](https://github.com/openfspht/openfsp/commit/a0c81cd1adc1d084ba1f91e87bd22f63fd47803e))
* encode the payment, key, operator and simulated payment identifiers in API paths ([cdc614f](https://github.com/openfspht/openfsp/commit/cdc614fab491cce2fcf9eedc56efbcdba79056bd))
* evict the least recently used rate limit bucket instead of refusing new clients when the table is full ([a72fb1e](https://github.com/openfspht/openfsp/commit/a72fb1ee3d7858d39f771279b9fbb97f4962d94f))
* exit with a one line message and its own code when the server under test cannot be reached ([2c7891c](https://github.com/openfspht/openfsp/commit/2c7891c5cab85806d4659e09a5e3acbe78a08b35))
* expect capability-not-supported without the capability and trace the clauses of the current specification ([51f2cff](https://github.com/openfspht/openfsp/commit/51f2cff8e1ca7004d9cff115ae8d32fe70d7a8d0))
* fence idempotency leases by a claim generation and treat a key past retention as new ([ecf8bbb](https://github.com/openfspht/openfsp/commit/ecf8bbb5bca863e66ff91352f4f52794816e1743))
* fingerprint a simulated creation on its canonical form without null members and with NFC strings ([ea0bb7b](https://github.com/openfspht/openfsp/commit/ea0bb7ba6170f10b7e7873f3542835c26e78462d))
* fingerprint gateway requests with the protocol rule and drop the legacy fingerprint ([75e2600](https://github.com/openfspht/openfsp/commit/75e26007f52be5113393c5dffe9f0ada99a7d62c))
* fingerprint idempotent requests with JCS after removing nulls and normalising strings to NFC ([0396e68](https://github.com/openfspht/openfsp/commit/0396e68459f7e48b8ff4ece6d777996617f7d907))
* fingerprint simulator requests with the protocol rule, keeping metadata as received ([205ac9b](https://github.com/openfspht/openfsp/commit/205ac9b5bef71b5b419bc4c05ee9d7c5e51231bd))
* forbid caching the response that shows an issued API key ([9fa5f81](https://github.com/openfspht/openfsp/commit/9fa5f815dc3d2582555966df382ca3a1d98ea2ba))
* generate the local application key privately and only for the targets that need it ([3ea3244](https://github.com/openfspht/openfsp/commit/3ea32447607987ba74a8537770273f2d3f8630fc))
* give a replayed simulated response a fresh Request-Id and keep the stored body ([3870735](https://github.com/openfspht/openfsp/commit/3870735018ac09b0e5c170d58ae936c868119ab7))
* give the simulated preparation expires_at to the second, as the fixtures schema requires ([8725240](https://github.com/openfspht/openfsp/commit/872524029c66157521698039f8246eb31fc12669))
* keep loopback addresses in the development profile and require public addresses elsewhere ([7b61e1d](https://github.com/openfspht/openfsp/commit/7b61e1d44712491309e03f38c68fdc8287855ca3))
* keep on a payment only the expires_at the provider guarantees ([fea3ada](https://github.com/openfspht/openfsp/commit/fea3ada85c83761bd163fd9cbd57bca9fa689e43))
* keep one active key per secret when gateways bootstrap concurrently ([15806b8](https://github.com/openfspht/openfsp/commit/15806b819804ba807ecefe9507cc04300379cee7))
* keep the console sign-in redirect on the console origin ([e3f21ed](https://github.com/openfspht/openfsp/commit/e3f21eda7fa8183f7e2fa8ba9b8c03a458670abe))
* keep the default scenario and the automatic rotation of the simulator per merchant account ([48c27e6](https://github.com/openfspht/openfsp/commit/48c27e6ae51eeddf1cfc2608baaab1c5b85c0af3))
* keep the security headers of the validator interface on monitoring.json ([c73607a](https://github.com/openfspht/openfsp/commit/c73607ac1cd093788601735adf1bcfbb20a18718))
* keep the security headers on the console and simulator monitoring.json ([1cadd11](https://github.com/openfspht/openfsp/commit/1cadd114e88a4620691736db7e1f4e786190f909))
* keep the stored digits when the console indents an exchange body ([9c9bb92](https://github.com/openfspht/openfsp/commit/9c9bb92753f368796fe5e815a06203f2fffa3743))
* let a pending payment omit next_action in the payment schema, as API 4.1.1 allows ([3dbfd14](https://github.com/openfspht/openfsp/commit/3dbfd1416739508f3e7e2ef87e3e0248fa762920))
* let the simulator UI proxy present a key from its environment to the control interface ([1ff7631](https://github.com/openfspht/openfsp/commit/1ff76316707c447daf8e6bac86336ebc4bfc9462))
* limit provider calls triggered by unauthenticated callbacks and transaction id returns ([4c28885](https://github.com/openfspht/openfsp/commit/4c28885bd5d395c063e09dec25e11dc45b1d9144))
* make the striped rows of the results table visible ([43b4ddc](https://github.com/openfspht/openfsp/commit/43b4ddce69f395e307e676787e1fbaae23ef7cff))
* map an upstream 504 to provider-timeout and answer the simulator TIMEOUT scenario with a prompt 504 ([1a0806f](https://github.com/openfspht/openfsp/commit/1a0806f3e05234ed73b076c4410bdca1a8f85748))
* mark a scenario assisted only when it applies and still lacks its input, and fail webhook scenarios when nothing was delivered ([72934fa](https://github.com/openfspht/openfsp/commit/72934fa9de6f0455e098d16a674bfae08c25a9f2))
* mask a secret cut by the audit body size limit ([ef1fa91](https://github.com/openfspht/openfsp/commit/ef1fa91b301aae436dc503e9baf1b8e19f695726))
* mask the keys and access code typed in the validator form ([f7646bc](https://github.com/openfspht/openfsp/commit/f7646bccbbfa8cff8a9585ec3597dbd40711de52))
* move include-stacktrace to its Spring Boot 4 property ([354b6ed](https://github.com/openfspht/openfsp/commit/354b6ed945585a40566a86e70fa2095f2b59cc3d))
* never conclude expired from expires_at unless the provider guarantees expiry ([b2395e1](https://github.com/openfspht/openfsp/commit/b2395e102b511ffbace25cddba4e3f43af803184))
* read a single Bearer Authorization header in any case on the conformance endpoints ([af8ffbf](https://github.com/openfspht/openfsp/commit/af8ffbf5d452c25370d81e5d6b51fe34a108f46a))
* read an unknown failure reason as unspecified in the SDKs ([9013361](https://github.com/openfspht/openfsp/commit/9013361706aaf041a25f425107f39c165c656dbe))
* read forwarded headers only from the proxy addresses the operator configures ([9f7b3ca](https://github.com/openfspht/openfsp/commit/9f7b3ca2a17095c597172dd3f82d77e9587ed2ac))
* read the demo from the active profiles only and accept an uppercase https scheme ([fe98611](https://github.com/openfspht/openfsp/commit/fe9861136403ad39c5d5807438fccc0616339816))
* record admin sessions on the server and revoke them at sign-out ([5cf4a92](https://github.com/openfspht/openfsp/commit/5cf4a92dce51886fed5779530d2239679008f413))
* redact audit bodies from parsed json and drop the ones that do not parse ([341dd37](https://github.com/openfspht/openfsp/commit/341dd37ffb1f095222bfc8f8cb20471ebd9ef462))
* refuse an API handler that declares no scope ([72693c2](https://github.com/openfspht/openfsp/commit/72693c2ec4812234439c4501c5ced4dabb4d8755))
* refuse application keys published in the repository history ([1a328a6](https://github.com/openfspht/openfsp/commit/1a328a64799a02c0348898694abb2b3ff1c5960d))
* refuse shared, benchmarking, reserved, broadcast, 6to4 and local NAT64 addresses as public ([43c0b71](https://github.com/openfspht/openfsp/commit/43c0b71fcb69834a9f6306153201b00eed5e49f3))
* reject a webhook with an unknown previous_status in the Kotlin SDK as the TypeScript SDK does ([06a31e4](https://github.com/openfspht/openfsp/commit/06a31e4b26e48718b4e53daa5bd5490625421631))
* replay a recorded creation with its Location and a Request-Id of its own ([825a9aa](https://github.com/openfspht/openfsp/commit/825a9aa28c73e90f1db2e6090d57de6beb05557c))
* report an unreachable server under test as such instead of an internal error ([2b3ba0c](https://github.com/openfspht/openfsp/commit/2b3ba0c98b85cf4f468334f7b06d6bece83ca3ca))
* require a test key and a preparation answer valid against the fixtures schema, control_endpoint included ([1dc2163](https://github.com/openfspht/openfsp/commit/1dc21638c89bf82c0679170a5679379d2ff64eae))
* require the amount before settling a success from a provider that reports amounts ([1738dc9](https://github.com/openfspht/openfsp/commit/1738dc9ec614495dfe0e24b4d362dea31262a16f))
* restrict the simulator control routes to the loopback or a merchant account key and mask every key in the journal ([c0ab219](https://github.com/openfspht/openfsp/commit/c0ab219bde65452ba01b29a0ce399713157d1cde))
* run the Core outcome scenarios at any provider by reaching their states through the control interface ([13d683b](https://github.com/openfspht/openfsp/commit/13d683bee5529b6c28532f1ed3bf32bac4e96503))
* seal webhook signing private keys at rest under the application key ([6511c79](https://github.com/openfspht/openfsp/commit/6511c79c85681d0dd1b382756b03a7cd008ee238))
* send expiresAt with millisecond precision from the Kotlin SDK ([d93f149](https://github.com/openfspht/openfsp/commit/d93f149b05a609103df7ff6043581da39762f380))
* serialize make keys so concurrent runs keep a single application key ([0126f29](https://github.com/openfspht/openfsp/commit/0126f2977a824a06b299f76cf6d1edef52a7b76e))
* serve only simulated providers in the demo, whose credentials are published ([a0a506e](https://github.com/openfspht/openfsp/commit/a0a506e94523a04df68e2b24a2bd13cc2faf6a44))
* show the report retention the validator announces instead of a fixed 90 days ([64bc0a6](https://github.com/openfspht/openfsp/commit/64bc0a61fe6ccd7534c0fbbbd97671fc3e40135f))
* show translated texts for HTTP, network and session errors ([3951639](https://github.com/openfspht/openfsp/commit/3951639b1e4d65b3fc7e07369a76910670f6eb4d))
* start the gateway with production defaults and refuse the published demo admin token ([a78323a](https://github.com/openfspht/openfsp/commit/a78323a64a058f6510c005b8e5139efacb0fe516))
* start the validator outside development unless a profile chooses it ([d34b6a6](https://github.com/openfspht/openfsp/commit/d34b6a6048c6d1dacf3870b60dc50f342d067e38))
* stop naming the environment as the source of the bootstrap API key ([a08491c](https://github.com/openfspht/openfsp/commit/a08491cf0427b2a27e555ba8cf6cf3a7357b2caa))
* take a validator run slot before any request to the target ([b9a1a4f](https://github.com/openfspht/openfsp/commit/b9a1a4f99472bf386ef77ad48fe5e372ed46921b))
* take the validator client address from the last untrusted X-Forwarded-For entry ([f0f9c64](https://github.com/openfspht/openfsp/commit/f0f9c64dfb9eff7cb89c4f424c5444b35d3d6a47))
* tie demo credentials to the demo profile and keep credentialed providers out of the demo, conformance and plain http ([efb9de9](https://github.com/openfspht/openfsp/commit/efb9de91f30b6901d9f90ab61dcce08834735a04))
* treat a redirect without URL or an unknown next action as an unexpected response in the TypeScript SDK ([f13a092](https://github.com/openfspht/openfsp/commit/f13a092ed241e184354d65accd53c566bddb442b))
* validate the database schema at startup and map the report digest as varchar ([c0d51c7](https://github.com/openfspht/openfsp/commit/c0d51c7e6cf0b74d91d3144b36a2d97c108582d2))
* verify provider notifications with the credentials the payment recorded ([e55ce05](https://github.com/openfspht/openfsp/commit/e55ce05212d00925c70a17c199302d7687ff4a96))
* verify webhook signatures over the received Signature-Input as an RFC 9421 dictionary ([dbf09f4](https://github.com/openfspht/openfsp/commit/dbf09f46292bfa80812250ddd402dde3d2ac111b))
* write the application key atomically and stop when it cannot be generated ([7ae8ecc](https://github.com/openfspht/openfsp/commit/7ae8ecc9bfa75cb5c29c9b96d9020e08b3c9d5c5))


### Build System

* ship one image per service that serves its own interface instead of separate interface images ([1ef48bd](https://github.com/openfspht/openfsp/commit/1ef48bdc4029a9730d04b0071fadb158756ae332))

## [0.4.0](https://github.com/openfspht/openfsp/compare/v0.3.0...v0.4.0) (2026-11-18)


### Features

* declare amount bounds on the NatCash mock ([dcc65f1](https://github.com/openfspht/openfsp/commit/dcc65f1e23a14904fab7098b7d984f3d2dde8eae))
* deliver a conformance receiver only the events of its own payments ([099c905](https://github.com/openfspht/openfsp/commit/099c9055c000d9f061c17bd1e402459c8081bed6))
* let the gateway prepare conformance runs in the test environment ([0b659ed](https://github.com/openfspht/openfsp/commit/0b659ed22c16eaeecd00a9f968389d9f78703e06))
* let the simulated OpenFSP operators prepare conformance runs ([b85decf](https://github.com/openfspht/openfsp/commit/b85decf3d19068b6918e603ac709331bc3de5fe7))
* prefer a provider that requires no return URL ([71fba8b](https://github.com/openfspht/openfsp/commit/71fba8ba98f4b2bacfed3ecd33b00850f1838308))
* read the conformance endpoint from the configuration document ([3c52ebb](https://github.com/openfspht/openfsp/commit/3c52ebb1b711f0b36f17dbf70b28baeaf25d162f))
* run the validator without a simulator ([c78695a](https://github.com/openfspht/openfsp/commit/c78695aba422a7982d7bee6faa06d57b42f92836))
* validate a server from its URL alone ([68ed385](https://github.com/openfspht/openfsp/commit/68ed385b886a2e7335fb5aaea971d001b6a755ec))


### Bug Fixes

* drop the environment badge from the validator ([c556794](https://github.com/openfspht/openfsp/commit/c5567940cf2fb85b0c237b728398a0a304a04256))
* drop the single-choice target selector from the validator ([2febd03](https://github.com/openfspht/openfsp/commit/2febd03f1e806d8e261c6aa53b5c11b8154f2dbb))
* journal a simulator request before answering it ([634e088](https://github.com/openfspht/openfsp/commit/634e0886c17edd388352a5d98b74bfe420f68f6c))

## [0.3.0](https://github.com/openfspht/openfsp/compare/v0.2.0...v0.3.0) (2026-11-17)


### Features

* expose a health endpoint on the simulator and the interfaces ([a057110](https://github.com/openfspht/openfsp/commit/a05711010e2bd384658b14b822a0540a63b1a755))

## [0.2.0](https://github.com/openfspht/openfsp/compare/v0.1.0...v0.2.0) (2026-11-17)


### Features

* report errors to Sentry when a DSN is configured ([c171f8a](https://github.com/openfspht/openfsp/commit/c171f8aac36bebb8a8f0fbbd2508a0d91f7d9a82))

## 0.1.0 (2026-11-17)


### ⚠ BREAKING CHANGES

* require the idempotency key in the TypeScript payments create call
* require the idempotency key in the Kotlin payments create call

### Features

* add the conformance requirements, scenarios, schemas and OpenAPI contract ([6e63895](https://github.com/openfspht/openfsp/commit/6e63895940a17f25bf40199bdfb63c295866d0e7))
* add the conformance validator with its command line and web service ([7a6f3ed](https://github.com/openfspht/openfsp/commit/7a6f3ed627211fefcc90e918777f600e8da25508))
* add the console, simulator UI, validator page and demo shop ([a6eed0e](https://github.com/openfspht/openfsp/commit/a6eed0e9e8e7895a8dce60124c2394e42d2384fe))
* add the gateway ([8458fb6](https://github.com/openfspht/openfsp/commit/8458fb6773edcaa3b69f07ff8656d72e870ee422))
* add the Kotlin and Java SDK ([4de995b](https://github.com/openfspht/openfsp/commit/4de995b435c615ada36c2f9bd39a67f48d7fe1ed))
* add the protocol, rules and domain annotations shared by every module ([7ea590c](https://github.com/openfspht/openfsp/commit/7ea590cb0c2259a60f4f2da8dfdbbc9871241879))
* add the provider simulator ([431168c](https://github.com/openfspht/openfsp/commit/431168c7a771bdce609c26eab8d9cfddc89dea40))
* add the TypeScript SDK ([036c986](https://github.com/openfspht/openfsp/commit/036c9863186d395ac650de3fd71b1a489b6d645f))
* align the app copy with the specification's terms ([c0bb1e4](https://github.com/openfspht/openfsp/commit/c0bb1e42443504322f23ef319c0fdefda5086b2e))
* declare a mock_openfsp scenario under the payment reference ([0507ae5](https://github.com/openfspht/openfsp/commit/0507ae5f6a4898e442f2b53a977dff43b63bd474))
* default the validator service to validate.openfsp.org ([3ba26d4](https://github.com/openfspht/openfsp/commit/3ba26d42b5617deec1ce643c11a704b2fb198f81))
* offer only the server target in the validator page ([74689bc](https://github.com/openfspht/openfsp/commit/74689bcb8daf632904fb873561d0e5535e1e3c9c))
* publish gateway upstream data in metadata and problem detail ([288b996](https://github.com/openfspht/openfsp/commit/288b996e040fd8e6f13920f3dc4289885f4722af))
* report a simulated payment state by reference on the control API ([955f7af](https://github.com/openfspht/openfsp/commit/955f7afc20b0243d717d31b0692de518b31a4b0b))
* serve the gateway statement at /gateway/statements outside the v1 namespace ([df696f6](https://github.com/openfspht/openfsp/commit/df696f6b43315d57c3a7f27d1cfa156450db5d5f))
* show the gateway metadata keys in the console payment detail ([a9203c3](https://github.com/openfspht/openfsp/commit/a9203c396fa2c5dae57392c6c5c811fe723a152e))
* test the standard against the server and client targets only ([90bfa29](https://github.com/openfspht/openfsp/commit/90bfa299828d2faca1ca0e6b0f8d4844348bcd9d))


### Bug Fixes

* add a webhook endpoint in one transaction ([1cbecc6](https://github.com/openfspht/openfsp/commit/1cbecc67999987712e08d7c6b12a71c6e4b12bdd))
* answer a resend from the creation request stored on the payment instead of creating upstream again ([f39ddae](https://github.com/openfspht/openfsp/commit/f39ddaefab8fe733f0cdbc09be6bafb5d1403a86))
* answer simulator control and space errors with problem documents ([8903030](https://github.com/openfspht/openfsp/commit/89030303a6ec35777c28b6aa4a908711c9e072d8))
* bound webhook deliveries in flight by the connection pool and log their failures ([685035e](https://github.com/openfspht/openfsp/commit/685035eb59a74719eff568f8124e7dbb9dcb18bb))
* cap the request journal and purge old default space records ([a8a4b1b](https://github.com/openfspht/openfsp/commit/a8a4b1b5c1053a9fe55312a8faa2da636f0c5772))
* carry the requested expiry to the provider and compare expiry and locale on a resend ([a8240ca](https://github.com/openfspht/openfsp/commit/a8240cad078d6637e0b943bed56cb5108811a467))
* check suite pointers and patterns, keep a bad step inconclusive and give command line errors their own exit codes ([83d9f35](https://github.com/openfspht/openfsp/commit/83d9f35025af9602bf87ab75bb75292ac7917144))
* close the tokenless admin outside development mode and read client addresses behind a TLS proxy ([dbece31](https://github.com/openfspht/openfsp/commit/dbece315e284c9d8f69ba361ec23e4b743f47da5))
* compare a member against a capture that was absent ([138d6b7](https://github.com/openfspht/openfsp/commit/138d6b795eec42e893ef39126099c40d6f4420ae))
* count abandoned deliveries of an endpoint atomically ([fee1152](https://github.com/openfspht/openfsp/commit/fee1152b1be5b93adc74decc8cf6d25e1617aaeb))
* create the first signing key once under a lock at startup and keep discovery read-only ([d9efeb2](https://github.com/openfspht/openfsp/commit/d9efeb27536db731a52d5ae951cc615b2a72f73a))
* decode an attestation document only when a page needs it ([7ebffd7](https://github.com/openfspht/openfsp/commit/7ebffd7f24475cda60a95e34b2dc7b41954d0f98))
* delete the extracted copy of the bundled conformance suite once loaded ([3bb3612](https://github.com/openfspht/openfsp/commit/3bb361219b7dd8957786cbfd611d027d9f5bac95))
* deliver run events to followers outside the run session lock ([9037a4d](https://github.com/openfspht/openfsp/commit/9037a4def2978fcbb24263eec5385e519214c7d3))
* forget only a payment whose creation is still in flight, with its whole journal ([0a40e00](https://github.com/openfspht/openfsp/commit/0a40e0068df823587594b66be9130b3c474350df))
* give back the rate limit slot of a run the simulator could not open ([cb933dc](https://github.com/openfspht/openfsp/commit/cb933dcd4d61e92eff599e5febc89de62092cec0))
* hand simulation timers off to a bounded worker pool and log their failures ([d20ac24](https://github.com/openfspht/openfsp/commit/d20ac241ae4e842b9cfa2987f432ecc668881618))
* issue a key and its principal in one transaction with unique principal names ([ab08b3c](https://github.com/openfspht/openfsp/commit/ab08b3c61988b1356b3c1ea7eef4d28a310d1f73))
* keep attestations when their report is purged ([9e1fee1](https://github.com/openfspht/openfsp/commit/9e1fee121b9301083e46be10562ba1e14b4a472e))
* keep Kotlin SDK providers for a minute instead of refetching on every call ([14d72a3](https://github.com/openfspht/openfsp/commit/14d72a3161d25930664575d4ca0eda9c4e7500d1))
* keep one simulated payment per reference under concurrent creations ([57d2a29](https://github.com/openfspht/openfsp/commit/57d2a29c5ed533f3554e838cf175188e1a1f4b8e))
* keep the legacy simulator hang and late callback keys working ([0dc7836](https://github.com/openfspht/openfsp/commit/0dc7836221bcbc2c7be3648fe57e8aca56e5869e))
* keep TypeScript SDK providers for a minute instead of refetching on every call ([77547fc](https://github.com/openfspht/openfsp/commit/77547fc415ce4bc17eb8f9117067c5e71c5c2108))
* let a failed Kotlin webhook key refresh retry after a short backoff ([7f027c0](https://github.com/openfspht/openfsp/commit/7f027c01c69a3bbd6c2394efcfcc5c372b9c7acb))
* let a failed TypeScript webhook key refresh retry after a short backoff ([fe54def](https://github.com/openfspht/openfsp/commit/fe54def1b6db6742e8579d4b64c88a1a60fa9d6b))
* log a failed audit write instead of failing a served response ([2e5abdd](https://github.com/openfspht/openfsp/commit/2e5abddbea608d5df9a612259e5682a2f05742c0))
* make the Kotlin FakeClock safe to advance from several threads ([df3f8cb](https://github.com/openfspht/openfsp/commit/df3f8cb8747f10e26801de49f7b9dfbfe5ad0798))
* make the Kotlin testing fixtures follow the specification ([e9454b2](https://github.com/openfspht/openfsp/commit/e9454b2363f88d3187aabd7d1eb1589c164c6955))
* make the TypeScript testing fixtures follow the specification ([08b9a13](https://github.com/openfspht/openfsp/commit/08b9a13d7a07a1c99328d83afa47b89a42c49d83))
* map an interrupted Kotlin retry wait to a transport failure ([013487e](https://github.com/openfspht/openfsp/commit/013487e3ba0b15fda9e572d9379154535982043a))
* map the discovery document to an SDK configuration model in the TypeScript SDK ([028d624](https://github.com/openfspht/openfsp/commit/028d6247258162ead231e8a77f3e40eedd86c21a))
* point traceability at the moved validator tests ([45c9b2c](https://github.com/openfspht/openfsp/commit/45c9b2c703958d7b12cad9b59adf27b831076aae))
* rate limit provider callbacks like the other anonymous endpoints ([89738ef](https://github.com/openfspht/openfsp/commit/89738ef1dc8af9bb664bda04009df0458a390da2))
* read a minimal discovery document into an SDK configuration model in the Kotlin SDK ([47523a7](https://github.com/openfspht/openfsp/commit/47523a7658b8743fb992a1855380d607e4df853f))
* read simulator payments through a mapped client type and page through every match ([5712165](https://github.com/openfspht/openfsp/commit/5712165521b13c2bc11105ce67fe0487282dc063))
* reconcile pending payments in turns and leave a creation in flight alone ([c158cd5](https://github.com/openfspht/openfsp/commit/c158cd54edca6c6cf44eb644f0c70acc0ff6ebfd))
* record a fetched statement under a lock per provider and business day ([419dd06](https://github.com/openfspht/openfsp/commit/419dd06b1fd3c5c5a4d42fb06c2ff99ba3c935aa))
* redact the URL of an interrupted target request ([1566000](https://github.com/openfspht/openfsp/commit/1566000ec9a89db01d34cbddf3b082da8d923a8a))
* refuse a command line timeout that is not a positive number of seconds ([24e8af5](https://github.com/openfspht/openfsp/commit/24e8af5aed42c54c93fbbf83af0c030a922c3089))
* refuse an attestation on a terminal payment as a state conflict ([e5116f3](https://github.com/openfspht/openfsp/commit/e5116f3ab2ab1b022cf556a19fbb19fd7aaa97a6))
* refuse an unknown provider on every statement read as an invalid provider field ([6152c8e](https://github.com/openfspht/openfsp/commit/6152c8e4d5856db785abd09aed39a95eb17befa2))
* require the idempotency key in the Kotlin payments create call ([a51e20f](https://github.com/openfspht/openfsp/commit/a51e20fa60edb4d9a66afa42411f4501d0dedec6))
* require the idempotency key in the TypeScript payments create call ([f1d7ff6](https://github.com/openfspht/openfsp/commit/f1d7ff6aaad986dabdacfa2f7bcaeb8eef7ab32f))
* resend after a non-problem 502, 503 or 504 in the Kotlin SDK ([261d142](https://github.com/openfspht/openfsp/commit/261d1422e92082d70479cef66bd95006b286f205))
* resend after a non-problem 502, 503 or 504 in the TypeScript SDK ([de708b7](https://github.com/openfspht/openfsp/commit/de708b799621433ec52cfcdfae0c7dc2e8becf9f))
* resend an unknown creation with its original callback URL ([de0b47d](https://github.com/openfspht/openfsp/commit/de0b47d02a91108961cdb58d4420d829c9a91f92))
* send simulator callbacks through a guarded client with timeouts ([733de86](https://github.com/openfspht/openfsp/commit/733de865186401bbe280176334ef6a79c66e6bf2))
* serve statement entries, scopes and provider errors from mock_openfsp as the standard requires ([51e552a](https://github.com/openfspht/openfsp/commit/51e552a50050d233cfffaa6610f474be208d9c9f))
* settle a simulated payment once and number each callback attempt ([037cade](https://github.com/openfspht/openfsp/commit/037cadefc6d6768da2f620adf3485f6aa1a97c60))
* sign admin sessions with a key derived from the configuration so they survive restarts and replicas ([4f33f8b](https://github.com/openfspht/openfsp/commit/4f33f8b73a147535a3f2825e661a7df3ee59bee3))
* throw only documented errors from Kotlin webhook verification ([c5dbe0a](https://github.com/openfspht/openfsp/commit/c5dbe0a1aed44f1ea0707cb431b818b7a18157db))
* throw only documented errors from TypeScript webhook verification ([19c3956](https://github.com/openfspht/openfsp/commit/19c39564cbafa073c2bb0ce651efd25abd6841e7))
* type the validator run form submit event ([ab554a4](https://github.com/openfspht/openfsp/commit/ab554a482787f3d9f241052b9089c90785e34fc5))
* validate statement entries from the control API before booking them ([596db0c](https://github.com/openfspht/openfsp/commit/596db0ca5b135ca11b2af5bb4706fb04aaec0b6f))
* verify TypeScript webhook signatures over the Signature-Input member as sent ([9568e35](https://github.com/openfspht/openfsp/commit/9568e35d009663f3492c46da625a13fdeb5886f3))
* version payments and lock before saving a resumed creation ([23e6d4e](https://github.com/openfspht/openfsp/commit/23e6d4ead534bcfccfee71bbe3693e5eb163c374))
* word the uncontrolled scenario hint without naming a command line flag ([7f6a497](https://github.com/openfspht/openfsp/commit/7f6a49753aa35f9b478ba29ae1a89121e80e1936))
