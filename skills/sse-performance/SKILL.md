---
name: sse-performance
description: Tune server-sent events pipelines for high connection counts, low memory overhead, and stable chunk delivery.
version: 0.1.0
triggers:
  - sse
  - streaming
  - qps
  - reactor
  - netty
  - throughput
---

# SSE Performance

Use this skill for streaming response design and performance reviews.

## Rules

- Minimize per-connection state
- Reuse serializers and immutable DTOs
- Avoid per-token logging on the hot path
- Emit heartbeat comments for long-lived idle streams when needed
- Backpressure provider output before writing to the socket
- Push blocking provider SDKs onto bounded elastic pools only as a fallback

## 500 SSE QPS baseline

- Stateless API tier behind Nginx/Envoy
- HTTP keep-alive enabled end-to-end
- Connection pooling to model backends
- Max in-memory buffers capped
- Async metrics export only
- Load test with synthetic token cadence before real providers
