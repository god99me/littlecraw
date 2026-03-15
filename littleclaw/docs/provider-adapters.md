# Provider adapter contract

`littleclaw` should keep HTTP/SSE transport stable while model providers evolve underneath it.

## Adapter boundaries

- `api`: validates requests, enforces auth and quotas, serializes JSON and SSE
- `application` / `chat`: builds the chat plan, resolves skills, applies policy defaults
- `provider`: owns upstream-specific request mapping, auth, retries, timeouts, and chunk parsing
- `infra`: owns shared HTTP clients, metrics, circuit breakers, and secret loading

## Minimal provider interface

The existing `ChatProvider` is a good start:

- `id()`: stable provider identifier for logs and metrics
- `stream(ProviderRequest)`: non-blocking token stream that emits provider chunks and a terminal done event

Keep it narrow. Do not leak OpenAI-, Claude-, or vLLM-specific DTOs into the chat orchestration layer.

## Provider responsibilities

Each adapter should:

- translate `ProviderRequest` into the upstream wire format
- enforce upstream timeout budgets
- map upstream failures into stable local error categories
- surface provider metadata for metrics, not business logic
- expose streaming in a backpressure-friendly `Flux`

## Error taxonomy

Normalize provider failures into a small set of categories:

- `provider_auth_failed`: bad key, bad workspace, expired token
- `provider_rate_limited`: upstream 429 or hard quota rejection
- `provider_timeout`: upstream did not produce the next chunk in time
- `provider_unavailable`: 5xx, broken stream, connection reset
- `provider_bad_request`: malformed payload or unsupported model settings

This keeps HTTP errors and operational dashboards consistent across adapters.

## Timeout budgets

For a 500 SSE QPS API tier, every adapter needs explicit budgets:

- connect timeout: small and fixed
- first-byte timeout: protects queue buildup before the first token
- inter-chunk timeout: aborts stalled streams
- total request timeout: caps stuck upstream sessions

Timeouts should be config-driven and tagged by provider/model in metrics.

## Suggested next adapters

1. OpenAI-compatible SSE adapter for broad compatibility
2. vLLM adapter for local high-throughput testing
3. Anthropic adapter once the local error model and observability shape are stable

## Production note

Use a shared reactive `WebClient` per provider family. Do not create a new client or connection pool per request.
