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

### Phase 2: stub async provider
- emulate variable latency and chunking
- validate timeout and backpressure behavior

### Phase 3: real provider
- OpenAI/Claude/vLLM adapter under quotas
- validate connection pooling and upstream failures

## Known bottlenecks to avoid

- blocking SDKs on event loops
- giant prompt assembly per request
- unbounded in-memory chat histories
- excessive MDC/context copying per token
- connection churn due to missing keep-alive
