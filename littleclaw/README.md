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
- Input guardrails for request size and message count
- Simple health endpoint and skill inspection endpoint

## Project structure

```text
src/main/java/ai/littleclaw
  api/       HTTP controllers and exception mapping
  chat/      request models, SSE service, pluggable engine
  config/    typed config
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

## Suggested next steps

- Add an OpenAI/Claude/vLLM adapter that implements `ChatEngine`
- Add Redis for session memory and rate limiting
- Add Micrometer + Prometheus for p95/p99 SSE latency
- Add authentication and tenant isolation
- Add a benchmark suite with k6 or Gatling
