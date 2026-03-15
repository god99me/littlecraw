# Context, RAG, MCP, and multi-channel plan

This iteration pushes `littleclaw` closer to an OpenClaw-like orchestration model without pretending the transport adapters are already complete.

## Context engineering

The request path now supports a structured context assembly stage before provider dispatch:

1. match local skills from the latest user turn
2. retrieve lightweight RAG snippets from configured local paths
3. summarize available MCP tools
4. attach normalized channel capabilities
5. assemble one provider-facing system prompt

That means prompt construction becomes an explicit application concern instead of ad-hoc string building inside provider adapters.

## RAG

Current implementation is a local filesystem retriever with simple keyword scoring.

What it is good for:

- grounding answers in local docs, memories, and skills
- proving retrieval boundaries and prompt-shaping flow
- providing a safe fallback before vector infrastructure exists

What should come next:

- chunking with metadata
- embeddings + vector index
- tenant/project scoping
- freshness controls and incremental indexing
- citation formatting in user-facing responses

## MCP

Current implementation adds an MCP registry that exposes configured tool descriptors to the planning layer.

It does not yet execute MCP tools. Right now it solves the interface problem first:

- where MCP server definitions live
- how tool inventories reach planning
- how provider-facing prompts learn what tools exist

Next steps:

- MCP client transport abstraction
- per-tool auth policy
- request/response audit trail
- timeout and concurrency controls per MCP server

## Multi-channel capability model

Instead of binding the app to one ingress shape, requests can now carry normalized channel metadata:

- `channel`
- `provider`
- `chatType`
- `userId`
- `conversationId`
- `messageId`

The registry currently models capability differences for channels such as:

- API/OpenAI-compatible HTTP
- Feishu
- Telegram
- Discord
- Slack
- WeChat
- WhatsApp

This is the same design direction OpenClaw takes: normalize ingress first, then layer policy and rendering decisions on top.

## What is still missing for true OpenClaw-like channel support

- webhook adapters per channel
- outbound formatter/renderers per channel
- attachment/media normalization
- reaction/thread/card abstractions in the output layer
- session binding and thread affinity
- per-channel auth and signature validation

## Recommended next implementation order

1. auth filter + tenant context middleware
2. outbound response renderer by channel capability
3. vector-backed RAG store
4. MCP client execution layer
5. channel webhook adapters
