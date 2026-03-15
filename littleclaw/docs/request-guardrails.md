# Request guardrails

These guardrails keep the API tier predictable while `littleclaw` grows toward a production chatbot.

## Current defaults

Configured in `src/main/resources/application.yml`:

- max messages per request: `40`
- max aggregate input chars: `16000`
- max chars per message: `4000`
- default `maxTokens`: `512`
- max allowed `maxTokens`: `4096`
- allowed roles: `system`, `user`, `assistant`, `tool`
- allowed temperature range: `0.0` to `2.0`

## Why these limits exist

- message-count cap: avoids pathological history fan-out
- aggregate-size cap: limits prompt assembly and provider cost
- per-message cap: prevents a single giant turn from dominating heap and latency
- `maxTokens` cap: keeps long-lived SSE streams from pinning connections too long
- role allowlist: rejects unsupported protocol variants early
- temperature bounds: keeps provider payloads valid and portable

## Edge guardrails still needed

The next safe production steps are outside the DTO validator:

- API key or JWT auth at the HTTP edge
- per-tenant concurrency caps for active SSE streams
- per-tenant token budgets and daily quotas
- request body byte-size limit at proxy and app layers
- idempotency and request IDs for retries
- circuit breakers around upstream providers

## Recommended edge policy for 500 SSE QPS

At the load balancer or gateway:

- cap request body size before the app sees it
- keep idle timeout above the expected longest stream
- enable connection reuse and upstream keep-alive
- rate limit by tenant, not only by IP
- reject anonymous streaming traffic by default

At the app layer:

- normalize defaults before provider dispatch
- emit stable validation errors instead of provider-specific ones
- reserve enough headroom so one tenant cannot consume all active streams

## What to benchmark with these guardrails

Use at least three request shapes:

1. short prompt, default `maxTokens`
2. medium history near normal production usage
3. worst-case allowed request near all configured limits

That mix is more honest than benchmarking only tiny prompts.
