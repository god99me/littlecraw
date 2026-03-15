# SSE admission control and tenant fairness

This note fills a gap between request validation and benchmark planning.

For a chatbot API that targets `500` SSE requests per second, correctness is not enough. The system also needs a clear admission policy so one tenant, one bad upstream, or one large request class cannot consume all stream capacity.

## Why admission control matters

SSE traffic is expensive in a different way than short REST calls:

- each accepted stream occupies connection, memory, and scheduler capacity for its full lifetime
- slow first-byte or inter-chunk behavior ties up capacity even when CPU is mostly idle
- a few large requests can crowd out many small ones if the server only limits request rate
- upstream stalls can create connection pileups faster than standard HTTP metrics reveal

Because of that, request validation alone is not enough. The app needs explicit rules for when to accept, delay, or reject new streams.

## Control points

Admission control should happen at three layers.

### 1. Edge gateway

At Nginx, Envoy, or the API gateway:

- authenticate before allowing streaming traffic
- enforce request body byte-size caps
- enforce per-tenant request rate limits
- preserve long enough idle timeout for expected stream duration
- prefer backpressure by rejecting excess load early instead of queueing it invisibly

The edge should reject obviously invalid or anonymous traffic before the app allocates stream state.

### 2. API tier

At the WebFlux app boundary:

- check per-tenant active-stream quota before provider dispatch
- check global active-stream headroom before accepting a new stream
- reserve capacity for high-priority tenants or internal traffic when needed
- reject requests whose predicted cost exceeds tenant policy
- attach request class labels for metrics and downstream policy

This is the main place to protect fairness.

### 3. Provider adapter

At the provider boundary:

- enforce connect, first-byte, and inter-chunk timeouts
- stop upstream work immediately on client disconnect
- map provider 429s and stalls into stable local errors
- optionally trip a circuit breaker when one provider family degrades

This protects the app from accepting streams that cannot make progress.

## Recommended quotas

For an initial production posture, track these limits per tenant:

- active streaming requests
- new streaming requests per second
- estimated input cost per minute
- estimated output token budget per minute
- concurrent near-limit requests

A simple policy shape is:

- small requests: higher concurrency allowance
- medium requests: normal allowance
- near-limit requests: much lower allowance

This keeps a tenant from consuming all capacity with a few very large prompts.

## Request classes

Classify each request before it enters the hot path.

Suggested classes:

- `small`: short prompt, small `maxTokens`, expected short stream lifetime
- `standard`: normal production chat shape
- `heavy`: near guardrail limits or long expected stream lifetime
- `priority`: trusted internal or paid tenant traffic with reserved headroom

Use the class for:

- quota decisions
- benchmark scenario labels
- alert routing
- autoscaling analysis

## Simple cost model

A first production version does not need a perfect token estimator. It just needs a stable approximation.

Example score:

```text
cost =
  message_count_weight +
  aggregate_input_chars_weight +
  requested_max_tokens_weight +
  stream_priority_weight
```

Possible use:

- reject when cost exceeds tenant hard cap
- allow only limited heavy requests in parallel
- decrement tenant budget as soon as the stream is admitted, not after completion

It is better to use a coarse but explicit model than to rely on raw request rate alone.

## Global headroom policy

Do not run the fleet at a theoretical maximum.

Keep explicit headroom for:

- reconnect storms
- rolling deploys or one-pod loss
- temporary provider latency spikes
- internal retries after upstream resets

A practical rule is to begin shedding low-priority or heavy traffic before the cluster reaches the hard connection ceiling.

## Failure behavior

When the system is under stress, fail in a predictable order:

1. reject anonymous or unauthenticated streaming traffic
2. reject heavy requests over tenant quota
3. reject low-priority tenants once global headroom is low
4. keep existing healthy streams alive whenever possible
5. return stable retryable errors for rate and capacity rejections

For operator sanity, all overload paths should produce distinct metrics and logs.

## Metrics required for admission control

In addition to general SSE metrics, track:

- active streams by tenant
- active streams by request class
- rejected streams by reason
- queued or pending admissions, if any
- tenant budget consumption rate
- provider stall rate by tenant and provider
- disconnect-to-cleanup latency

If these are missing, fairness issues will be hard to diagnose.

## Benchmark implications

The benchmark plan should explicitly test admission control, not just throughput.

Add scenarios that verify:

- one tenant cannot starve others with near-limit requests
- provider stalls cause rejection or shedding before memory drifts upward
- disconnect storms release quota quickly enough to admit fresh work
- degraded tenants see localized failures rather than fleet-wide collapse

A `500` SSE QPS target is believable only if these fairness checks pass under load.

## Recommended next implementation steps

1. introduce a `TenantContext` resolved in an auth filter
2. classify requests into `small`, `standard`, and `heavy` before chat planning
3. add an in-memory admission controller first, then move counters to Redis for multi-replica fairness
4. expose metrics for active streams, rejections, and cleanup latency before running serious benchmarks
5. teach the benchmark harness to generate mixed-tenant traffic instead of a single homogeneous load shape
