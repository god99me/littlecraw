# littleclaw architecture

## Goal

Build a Java chatbot platform with local skill resolution, SSE streaming, and an API tier that can scale toward 500 SSE QPS.

## Proposed modules

- `api`: HTTP transport, auth filters, request validation, SSE serialization
- `application`: orchestration layer for chat completion, skill resolution, policy checks
- `domain`: neutral chat/session/provider abstractions
- `provider`: LLM adapters such as OpenAI, Claude, vLLM, or internal models
- `skill`: load, parse, match, and eventually cache local skills
- `infra`: Redis, metrics, config, persistence, and rate-limit integrations

## Request flow

1. Client sends chat request to `/v1/chat/completions` or `/v1/chat/completions/stream`
2. API layer validates body size, turn count, auth, and tenant quotas
3. Application layer extracts latest user turn and resolves top matching skills
4. Policy layer constrains tool/provider access using tenant and model policy
5. Provider adapter starts token stream
6. SSE transport emits chunks and done event
7. Metrics and audit events are recorded asynchronously

## Multi-agent direction

For OpenClaw-style multi-agent support, keep the main API stateless and model agents as orchestrated workers:

- `router-agent`: receives user request and decides whether to answer directly or delegate
- `skill-agent`: resolves skills and composes context packs
- `tool-agent`: performs isolated tool calls under policy
- `review-agent`: optional guardrail for safety/risk-sensitive actions

A production version should represent these as asynchronous workflows over a queue rather than in-process recursion.

## State model

- Short-term session state in Redis
- Durable audit events in a database or object storage
- Provider tokens and secrets in a secret store
- Skill metadata cached in memory and refreshed on schedule or file watch

## Immediate next code steps

- Add auth and tenant context filter
- Add Redis-backed rate limiter
- Add provider adapters with timeouts and circuit breakers
- Add request IDs and structured logs
- Add benchmark harness and SLA dashboards
