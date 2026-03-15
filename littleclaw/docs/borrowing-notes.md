# Borrowing notes

This project is now deliberately borrowing design direction from OpenClaw and adjacent agent/chat runtimes, even where the implementation is still a starter version.

## Patterns worth keeping

### 1. Normalize ingress before orchestration

OpenClaw treats channels as normalized runtime context instead of letting every channel leak raw transport differences into core logic.

Applied in `littleclaw`:

- `RequestContextFilter`
- `ChannelRequest`
- `ChannelRegistry`
- `ChannelResponseRenderer`

### 2. Keep the core orchestrator separate from transport adapters

A strong runtime keeps the orchestrator focused on:

- planning
- context assembly
- policy
- execution lifecycle

not on webhook details or markdown quirks.

Applied in `littleclaw`:

- `ChatService` handles orchestration
- channel-specific rendering is moving into `render/`
- provider transport is isolated in `provider/`

### 3. Treat memory/session state as a replaceable subsystem

OpenClaw treats memory as layered and replaceable. That is the right instinct here too.

Applied in `littleclaw`:

- `ConversationStore` interface
- `InMemoryConversationStore`
- `RedisConversationStore`
- `CompositeConversationStore`

### 4. Make control messages first-class

Stop, regenerate, and command handling should not be hacks outside the protocol.

Applied in `littleclaw`:

- explicit `ChatAction`
- `finishReason`
- interrupt endpoint
- command routing before provider dispatch

## Patterns still missing

- provider failover policy
- typed tool-call and tool-result envelopes
- durable session persistence
- richer channel renderer policies for cards, attachments, and reply threading
- audit trail and usage accounting

## Current takeaway

The right direction is not "copy OpenClaw literally".
The right direction is to copy the stable boundaries:

- normalized channel context
- layered memory/session model
- protocol-first control semantics
- orchestrator separated from adapters
