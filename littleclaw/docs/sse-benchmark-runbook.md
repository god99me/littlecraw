# SSE benchmark runbook

This runbook turns the high-level performance notes into a repeatable benchmark plan for a production-targeted `littleclaw` API tier.

## Goal

Prove whether the API tier can sustain `500` streaming requests per second without:

- unbounded heap growth
- unstable first-byte latency
- excessive provider timeout amplification
- tenant fairness collapse under mixed request sizes

## Test boundary

Benchmark the API tier separately from the provider tier first.

### Stage A: transport-only baseline

Use a deterministic synthetic provider with fixed chunk cadence.

Purpose:

- measure WebFlux + Reactor Netty + SSE serialization overhead
- validate proxy timeout and keep-alive settings
- establish the ceiling before upstream model latency distorts results

### Stage B: degraded upstream simulation

Use an async stub provider with configurable:

- connect delay
- first-byte delay
- inter-chunk delay
- chunk count
- injected 429s
- injected stream resets

Purpose:

- verify timeout budgets and cancellation handling
- measure how stalls propagate into active-stream saturation
- validate that failed tenants do not drag down healthy tenants

### Stage C: real provider adapter

Use one real provider adapter with conservative quotas.

Purpose:

- compare first-byte latency against the synthetic baseline
- validate connection pooling and upstream retry policy
- measure how much headroom remains after real network variance

## Required request profiles

Every benchmark stage should include these request shapes:

1. `small`: 2 messages, less than `500` input chars, `maxTokens=128`
2. `medium`: 8 to 12 messages, around `4000` input chars, `maxTokens=512`
3. `near_limit`: near configured input limits, `maxTokens=2048` or the current safe cap for the scenario

Do not benchmark only tiny prompts. The near-limit profile is what exposes queue buildup and fairness problems.

## Load matrix

Run each profile across:

- target QPS: `100`, `250`, `500`, `650`
- concurrent active streams: `100`, `250`, `500`, `750`
- upstream mode: healthy, slow first-byte, slow inter-chunk, rate-limited, reset mid-stream

A useful report records both the requested load and the actually sustained load.

## Success criteria

For the transport-only and stub-provider stages, a production-candidate build should meet these targets:

- p99 first-byte latency stays within a small multiple of the configured first-byte timeout budget
- active streams plateau instead of growing without bound
- heap usage returns close to baseline after the run
- cancellations and client disconnects release server-side work quickly
- one noisy tenant cannot consume all stream capacity

For the real-provider stage, success means the API tier still has measurable headroom after upstream variance is included.

## Must-have instrumentation

Before trusting benchmark results, expose at least:

- requests started, completed, cancelled, failed
- active SSE streams
- first-byte latency histogram
- stream duration histogram
- provider connect, first-byte, and inter-chunk timeout counters
- provider error counts by normalized category
- bytes in and out per request class
- JVM heap used, GC pause, and event-loop pending tasks

If these metrics are missing, benchmark results will be hard to interpret.

## Guardrails to enforce during the run

Keep benchmark traffic inside the same limits intended for production:

- request body byte-size cap at proxy and app layers
- bounded message count and aggregate input chars
- bounded `maxTokens`
- per-tenant active-stream quota
- per-tenant request rate limit
- explicit cancellation on client disconnect

Benchmarking without guardrails usually produces misleading throughput numbers.

## Failure drills

Run these drills at least once per candidate build:

1. cut off upstream mid-stream and verify the SSE client gets a terminal error quickly
2. inject upstream 429s for one tenant and verify other tenants remain healthy
3. stall inter-chunk delivery long enough to trigger timeout and confirm stream cleanup
4. force a client disconnect storm and confirm active stream count falls promptly
5. restart one API pod during active load and verify the rest of the fleet absorbs traffic cleanly

## Benchmark tooling suggestion

Either `k6` or `Gatling` is fine, but the harness must support:

- long-lived streaming HTTP connections
- per-scenario request bodies
- per-tenant auth headers
- custom checks for first event and stream completion
- mixed healthy and failure scenarios in one run

## Recommended output format

For every run, capture:

- git commit or working-tree note
- JVM flags and container limits
- proxy configuration relevant to SSE
- provider mode and timeout settings
- request profile mix
- sustained QPS and active-stream peak
- p50/p95/p99 first-byte latency
- error breakdown by category
- memory and GC summary
- operator conclusion: pass, fail, or inconclusive

## Exit decision

Do not call the service production-ready for `500` SSE QPS until:

- Stage A is comfortably above target load
- Stage B shows bounded degradation under stalls and resets
- Stage C shows acceptable headroom with a real provider
- dashboards and alerts are defined for the same metrics used in the benchmark
