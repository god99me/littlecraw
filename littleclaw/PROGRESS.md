# Progress

## 2026-03-14 09:45 CST

Started and mostly landed the next implementation wave around real upstream integration and traffic control:

- wired the main chat path to expose provider identity and select a real OpenAI-compatible reactive adapter when `littleclaw.provider.type=openai`
- added `OpenAiChatProvider` with shared `WebClient`, streaming SSE chunk parsing, config-driven auth/model selection, and bounded downstream prefetch for backpressure
- added admission control before provider execution with per-tenant request windows, global stream caps, per-tenant stream caps, and `429` responses on rejection
- removed blocking `block()` usage from synchronous completion handling so the JSON completion endpoint stays reactive on WebFlux
- tightened Redis lease release handling so stream counters do not drift negative on cleanup
- added Redis-backed admission state as an opt-in mode, with in-memory fallback for single-node development
- added `X-Tenant-Id` request support so shaping, quota, and connection control can work at tenant granularity
- documented the new shaping layer in `docs/traffic-control.md` and updated README/roadmap pointers
- extended tests to cover the new service wiring and a tenant rate-limit rejection path

## 2026-03-14 07:30 CST

Added a safe production-readiness iteration focused on SSE admission control and tenant fairness:

- added `docs/sse-admission-control.md` to define where to enforce stream admission decisions across the gateway, API tier, and provider adapters
- documented a simple request-class and cost-model approach so `500` SSE QPS planning does not rely on raw request-rate limits alone
- defined overload and failure-order behavior to protect existing healthy streams before accepting new heavy traffic
- called out the fairness and quota metrics that should exist before benchmark results are treated as production-significant
- extended the benchmark posture conceptually by spelling out mixed-tenant and degraded-upstream scenarios that validate fairness, not just peak throughput

## 2026-03-14 05:30 CST

Added a safe benchmark-planning iteration focused on making the 500 SSE QPS target measurable instead of aspirational:

- added `docs/sse-benchmark-runbook.md` with a staged benchmark plan covering transport-only, degraded upstream simulation, and real-provider validation
- defined concrete request profiles, load matrix dimensions, failure drills, and pass/fail criteria for SSE-heavy traffic
- documented the minimum instrumentation needed to trust benchmark results, including first-byte latency, active streams, cancellations, provider timeout counters, and JVM signals
- tied benchmark runs back to the existing request guardrails so future load tests stay inside realistic production envelopes

## 2026-03-14 03:30 CST

Made a safe production-hardening iteration focused on request guardrails and provider boundaries:

- tightened `ChatService` validation to reject unsupported roles, oversized single messages, oversized total input, invalid temperature, and oversized `maxTokens`
- normalized request defaults so provider dispatch always carries bounded `maxTokens` and temperature values
- extended config with explicit guardrail settings in `src/main/resources/application.yml`
- added `docs/request-guardrails.md` to capture app-level limits, edge policy, and benchmark shapes
- added `docs/provider-adapters.md` to define adapter responsibilities, timeout budgets, and a stable provider error taxonomy
- updated architecture, performance, roadmap, and README docs to point at the new production notes
- added regression tests for oversized `maxTokens` and unsupported message roles

## 2026-03-14 02:00 CST

Built the first `littleclaw` Java project skeleton with:

- Spring WebFlux HTTP layer
- SSE streaming endpoint
- local skill parsing and matching
- provider abstraction (`ChatProvider`)
- starter docs for architecture, performance, and roadmap
- local workspace skills for Java backend architecture, SSE performance, and API security

## 2026-03-14 18:55 CST

Extended the design and code skeleton toward OpenClaw-like orchestration:

- added explicit `channel`, `context`, `rag`, and `mcp` module boundaries in the Java codebase
- introduced a context assembly stage that combines matched skills, retrieved local snippets, MCP tool inventory, and normalized channel metadata before provider dispatch
- added a starter local-filesystem RAG service over docs, memory, and skills so retrieval can be exercised before vector infra exists
- added a normalized multi-channel capability registry to model channel differences without coupling the core app to any single webhook format
- added MCP registry plumbing so configured MCP tool inventories can shape planning even before full MCP client execution is built
- updated tests and docs to reflect the new context-engineering path and OpenClaw-style channel direction

## 2026-03-14 19:20 CST

Extended the interaction protocol and control plane around the chat path:

- added request/response protocol fields for `requestId`, `conversationId`, parent linkage, regenerate targets, interrupt targets, and metadata
- added a built-in command layer to intercept predefined command words such as help, stop, regenerate, and ping before provider dispatch
- added active request tracking to support stop/interruption semantics on both completion and streaming flows
- added a direct interrupt endpoint and wired stream termination to cancellation checks
- added an AOP logging aspect for timing completion and stream operations
- documented protocol extensibility and current weak points in `docs/message-protocol-review.md`

## 2026-03-14 19:35 CST

Pushed the service closer to a product-grade protocol and middleware shape:

- upgraded the message schema to explicit `action` semantics instead of relying only on implicit field combinations
- added `finishReason` to completion and streaming responses so interruption and control outcomes are machine-readable
- introduced a request-context web filter to inject tenant, request-id, and channel defaults at middleware level
- added a minimal auth filter with static API-key validation hooks so auth is no longer only a future note
- updated tests and protocol docs to reflect the new `action`-driven message flow

## 2026-03-14 19:50 CST

Added a more product-like interaction layer on top of the protocol work:

- implemented in-memory conversation turn storage so regenerate requests can replay prior user messages instead of acting as placeholders
- introduced a dedicated channel response renderer so channel-specific formatting starts outside the orchestration layer
- wired regenerate to resolve by response id or latest conversation response and link new outputs back through `parentMessageId`
- updated docs to describe the regenerate path and renderer boundary explicitly

## 2026-03-15 01:20 CST

Continued the overnight hardening pass toward a more durable runtime shape:

- abstracted conversation persistence behind `ConversationStore` and added both in-memory and Redis-backed implementations with a composite selector
- split channel rendering toward policy-based handling by adding a render-policy registry instead of keeping all channel differences in one renderer branch table
- cleaned up regenerate coverage so the API tests exercise conversation-based replay rather than a placeholder path
- added `docs/borrowing-notes.md` to record which OpenClaw-like boundaries are being intentionally borrowed and which are still missing

## 2026-03-15 12:10 CST

Tightened the overnight control-plane work so it behaves more like a real threaded chat service:

- fixed streaming regenerate so `action=REGENERATE` now reaches the real replay + SSE generation path instead of returning only a control event
- fixed stop targeting so `parentMessageId` can resolve back to the originating `requestId` before cancellation
- bounded active request tracking and clear cancellation markers on request completion so registry state does not grow forever in the happy path
- upgraded stored conversation turns to keep both the original request messages and the assistant-appended transcript
- added conversation continuation so a follow-up request with the same `conversationId` can reuse prior transcript context instead of acting like an isolated single turn
- extended service and controller tests for stream regenerate, stop-by-response-id, continued conversation, and cancellation cleanup

## 2026-03-15 16:20 CST

Pulled a first enterprise-hardening slice into the codebase instead of leaving it as roadmap text:

- added actuator + Prometheus dependencies and management endpoint exposure for `health`, `info`, and `prometheus`
- added `ChatMetrics` counters/gauges/timers for auth, admission, chat requests, stream lifecycle, provider calls, and conversation storage
- upgraded auth from a bare static key list to support tenant-bound API keys that can assert or inject `X-Tenant-Id`
- standardized API error payloads with `requestId`, `tenantId`, and provider-specific details
- hardened the OpenAI-compatible provider with upstream status classification, retryable vs non-retryable errors, bounded retry/backoff config, and explicit `ProviderException`
- introduced conversation transcript policy with TTL and transcript trimming so continued conversations do not grow without limit
- updated tests to cover auth gating and the tighter conversation continuation path

## Blockers

- Java and Maven are not installed in this environment, so compile/test execution could not be validated locally
- ClawHub skill installs hit rate limits; proceeded by distilling needed backend guidance into local workspace skills to keep design moving

## Next best moves

1. install Java 21 and Maven
2. add provider error classification + auth filter
3. benchmark synthetic SSE load
4. move from fixed windows to weighted request-cost shaping
