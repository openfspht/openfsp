# Changelog

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
