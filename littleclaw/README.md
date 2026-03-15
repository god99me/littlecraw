# littleclaw

`littleclaw` is a high-performance Java chatbot skeleton built for SSE-heavy traffic and local skill discovery.

## Why this architecture

- `Spring WebFlux + Reactor Netty`: non-blocking request handling is the safest default when the target is 500 concurrent SSE QPS.
- `SkillRegistry`: scans local `SKILL.md` files and turns them into runtime context.
- `ChatEngine` interface: keeps the HTTP/SSE layer stable while you swap in a real model backend later.
- `Deterministic starter engine`: makes it easy to test streaming behavior without paying model latency during development.

## Features

- JSON chat completion endpoint: `POST /v1/chat/completions`
- SSE streaming endpoint: `POST /v1/chat/completions/stream`
- Local skill loading from `./skills` or bundled examples
- Skill matching based on trigger words and metadata
- Context assembly stage for skills, RAG snippets, MCP tool inventory, and channel metadata
- Local filesystem RAG over docs, memory, and skills as a safe starter retriever
- OpenAI-compatible reactive provider adapter behind `ChatProvider`
- Per-tenant rate limiting plus global and tenant stream quotas
- Regenerate, interrupt, and conversation continuation with stored turn state
- Simple health endpoint and skill inspection endpoint

## Project structure

```text
src/main/java/ai/littleclaw
  admission/ traffic admission and quotas
  api/       HTTP controllers and exception mapping
  channel/   normalized multi-channel capability model
  chat/      request models, orchestration, SSE service, pluggable engine
  config/    typed config
  context/   provider-facing context assembly
  mcp/       MCP registry and future tool client boundary
  rag/       retrieval services and snippet models
  skill/     skill loader and registry
```

## Run

Requirements:

- Java 21
- Maven 3.9+

```bash
mvn spring-boot:run
```

## Example request

```bash
curl -N http://localhost:8080/v1/chat/completions/stream \
  -H 'Content-Type: application/json' \
  -d '{
    "stream": true,
    "messages": [
      {"role": "user", "content": "Help me design a Java chatbot with skills"}
    ]
  }'
```

## Skill format

Each skill is a `SKILL.md` file with front matter:

```md
---
name: code-helper
version: 1.0.0
description: Assist with programming questions.
triggers:
  - java
  - code
  - architecture
---

# Code Helper Skill

Use this skill for engineering tasks.
```

## Getting to 500 SSE QPS

This codebase is designed for that target, but hitting it in production depends on the model backend and deployment shape.

Recommended production steps:

1. Keep the API tier stateless and run multiple replicas behind Nginx or Envoy.
2. Replace `HeuristicChatEngine` with an async upstream adapter that supports backpressure.
3. Keep connection counts high and avoid per-request thread creation.
4. Disable verbose logging on the hot path.
5. Use HTTP keep-alive and load balancer connection reuse.
6. Benchmark with a fixed token cadence before switching to a real LLM.

Suggested JVM flags:

```bash
java -server \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=100 \
  -XX:+AlwaysPreTouch \
  -XX:+ParallelRefProcEnabled \
  -jar target/littleclaw-0.1.0-SNAPSHOT.jar
```

## More production notes

- provider contract: `docs/provider-adapters.md`
- request limits and edge policy: `docs/request-guardrails.md`
- traffic shaping and stream quotas: `docs/traffic-control.md`
- context/RAG/MCP/channel plan: `docs/context-rag-mcp-channels.md`
- regenerate and rendering: `docs/regenerate-and-rendering.md`
- borrowing notes from OpenClaw-like designs: `docs/borrowing-notes.md`
- system design: `docs/architecture.md`
- benchmark phases: `docs/performance.md`

## Suggested next steps

- Add an OpenAI-compatible reactive provider adapter behind `ChatProvider`
- Add Redis for session memory, concurrency tracking, and rate limiting
- Add Micrometer + Prometheus for p95/p99 SSE latency and active streams
- Add authentication and tenant isolation
- Add a benchmark suite with k6 or Gatling
