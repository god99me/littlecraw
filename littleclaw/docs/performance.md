# Performance plan

## Target

- Sustain 500 SSE requests per second at the API tier with stable p99 latency and bounded memory growth

## Hot path rules

- No blocking calls on event-loop threads
- No synchronous disk I/O on request path
- No per-token debug logging
- Reuse object mappers and codecs
- Cap request body and history size before provider invocation

## Deployment baseline

- Java 21
- Spring Boot WebFlux
- Reactor Netty
- Nginx or Envoy in front
- Redis for rate limiting and short session state
- Prometheus + Grafana for dashboards

## Metrics to watch

- active SSE connections
- requests/sec
- tokens/sec
- provider latency p50/p95/p99
- first-byte latency
- heap used and GC pause time
- 429 rate and provider timeout rate

## Benchmark phases

### Phase 1: synthetic provider
- fixed-size token cadence
- validate transport overhead only
- goal: prove connection handling and SSE serialization
- run three payload profiles: short, medium, and near-limit requests

### Phase 2: stub async provider
- emulate variable latency and chunking
- validate timeout and backpressure behavior
- inject stalled-chunk and upstream-reset scenarios

### Phase 3: real provider
- OpenAI/Claude/vLLM adapter under quotas
- validate connection pooling and upstream failures
- compare p95 first-byte latency against the synthetic baseline

## Benchmark matrix

For every phase, record results across:

- concurrency: 100, 250, 500, 750 active streams
- response length: 128, 512, 2048 output tokens
- prompt shape: short, medium, near-limit
- failure mode: none, 429s, connect timeout, inter-chunk stall

A good benchmark report should answer two questions:

1. What is the highest sustained SSE QPS before p99 first-byte latency or memory growth becomes unacceptable?
2. How fast does the system recover after provider stalls or quota errors?

## Known bottlenecks to avoid

- blocking SDKs on event loops
- giant prompt assembly per request
- unbounded in-memory chat histories
- excessive MDC/context copying per token
- connection churn due to missing keep-alive
