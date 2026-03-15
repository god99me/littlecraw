# littleclaw architecture

## Goal

Build a Java chatbot platform with local skill resolution, SSE streaming, and an API tier that can scale toward 500 SSE QPS.

## Proposed modules

- `api`: HTTP transport, auth filters, request validation, SSE serialization
- `application`: orchestration layer for chat completion, context assembly, policy checks
- `domain`: neutral chat/session/provider abstractions
- `provider`: LLM adapters such as OpenAI, Claude, vLLM, or internal models
- `skill`: load, parse, match, and eventually cache local skills
- `rag`: retrieval layer for local docs, vector search, and citation models
- `mcp`: MCP tool registry and execution boundary
- `channel`: normalized ingress/egress capability model for Feishu, Telegram, Discord, Slack, API, and more
- `infra`: Redis, metrics, config, persistence, and rate-limit integrations

## Request flow

1. Client sends chat request to `/v1/chat/completions` or `/v1/chat/completions/stream`
2. API layer validates body size, turn count, auth, channel metadata, and tenant quotas
3. Application layer extracts latest user turn and resolves top matching skills
4. Context layer adds RAG snippets, MCP tool inventory, and channel capabilities
5. Policy layer constrains tool/provider access using tenant and model policy
6. Provider adapter starts token stream
7. SSE transport emits chunks and done event
8. Metrics and audit events are recorded asynchronously

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

## Request guardrail posture

Current app-level validation should reject oversized or unsupported requests before provider dispatch:

- bounded message count
- bounded aggregate input chars
- bounded per-message chars
- bounded `maxTokens`
- bounded temperature range
- allowlisted chat roles only

See `docs/request-guardrails.md` for the edge-policy checklist that should sit in front of the app.

## Provider contract

Keep provider adapters behind `ChatProvider` so transport and orchestration do not depend on provider SDK details.

See `docs/provider-adapters.md` for adapter responsibilities, error categories, and timeout budgets.

## Immediate next code steps

- Add auth and tenant context filter
- Harden provider error mapping and timeout classification
- Add request IDs and structured logs
- Add benchmark harness and SLA dashboards
