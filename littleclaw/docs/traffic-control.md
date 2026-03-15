# Traffic control

This iteration adds the minimum control plane needed to keep SSE traffic from collapsing under bursts.

## What is implemented

- request admission happens before provider invocation
- per-tenant request windows reject excess bursts early with HTTP `429`
- active stream quotas protect both the whole node and each tenant
- stream output uses Reactor `limitRate(...)` so slow downstream consumers apply pressure upstream instead of growing buffers invisibly
- `X-Tenant-Id` is the default tenant routing input for quota decisions

## Shaping model

The current shaping model is intentionally simple:

1. fixed request window per tenant
2. global active-stream ceiling
3. per-tenant active-stream ceiling
4. bounded downstream prefetch for streamed chunks

This is enough to prove the controller boundaries before moving to weighted request classes or token-cost scheduling.

## Redis mode

When `littleclaw.admission.redis-enabled=true`, request windows and active-stream counters move to Redis so multiple API replicas can share one admission view.

Relevant keys:

- `littleclaw:admission:rate:{tenant}`
- `littleclaw:admission:stream:global`
- `littleclaw:admission:stream:tenant:{tenant}`

If Redis is disabled, the same rules run in-memory on a single node.

## Provider backpressure

Provider adapters should emit a non-blocking `Flux` and avoid buffering the full response. The OpenAI-compatible adapter does this by:

- using a shared reactive `WebClient`
- streaming SSE frames directly
- extracting deltas incrementally
- keeping downstream prefetch bounded

## Next upgrades

- weighted cost model based on prompt size and requested `maxTokens`
- separate budgets for sync JSON completions vs SSE streams
- inter-chunk timeout classification in the provider error model
- gateway-side connection shaping in Nginx or Envoy
