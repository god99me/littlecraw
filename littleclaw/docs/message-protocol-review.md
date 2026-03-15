# Message protocol review

## Current direction

The message protocol is now moving away from a minimal chat-only body toward a control-capable envelope.

Current request fields include:

- `messages`
- `stream`
- `maxTokens`
- `temperature`
- `channel`
- `conversationId`
- `requestId`
- `parentMessageId`
- `regenerateFromResponseId`
- `interruptRequestId`

Current response fields include:

- `id`
- `createdAt`
- `model`
- `activeSkills`
- `content` or `delta`
- `requestId`
- `conversationId`
- `parentMessageId`
- `metadata`

## What is good about it

### 1. Control actions do not require a brand new protocol

Stop/interruption and regenerate can ride on top of the same envelope shape.
That is a good sign: the protocol is not locked to a one-shot completion model.

### 2. Conversation threading is explicit

`conversationId` and `parentMessageId` make multi-turn threading and regeneration easier to reason about.
This is important once channels like Discord threads or Feishu cards enter the system.

### 3. The `metadata` bag gives safe forward-compatibility

Instead of breaking the top-level schema every time a new concern appears, low-risk expansion can happen in `metadata`.
That is useful for:

- citations
- channel rendering hints
- tool traces
- latency stats
- policy decisions

## What is still weak

### 1. Request body mixes content and control too loosely

Today the request can express chat content and control operations at the same time.
That works for early iterations, but long-term it should likely become one of:

- `mode: chat | control`
- `action: complete | stream | stop | regenerate`

Otherwise validation rules will become messy.

### 2. `metadata` is flexible but can become a junk drawer

If left unmanaged, `metadata` will turn into an untyped dumping ground.
It should eventually be split into clearer nested sections such as:

- `usage`
- `retrieval`
- `tools`
- `channel`
- `policy`

### 3. There is no tool-call or citation schema yet

If MCP execution and RAG citations become first-class, the protocol should add typed sections instead of hiding everything in plain text.

Recommended future fields:

- `citations`
- `toolCalls`
- `toolResults`
- `finishReason`
- `interrupted`

## Recommended protocol evolution

### Near term

- `action` is now present with values like `complete`, `stream`, `stop`, `regenerate`, `command`
- `finishReason` is now present on responses and chunks
- next step: add typed `retrieval` and `tools` sections in `metadata`

### Mid term

- split content messages from control events
- introduce typed assistant event envelopes for chunk, done, tool_call, tool_result, interrupt, and control_ack
- add versioned protocol namespace beyond the current metadata marker

### Long term

- support channel-specific rendering hints without changing core content semantics
- support agent-to-agent protocol messages
- support audit-safe replay of a whole conversation transaction

## Bottom line

The current protocol is already noticeably more extensible than the original minimal chat body.

**Highlight:** it is now good enough to support interruption, regeneration, channel context, and future tool/RAG wiring without an immediate rewrite.

But it is not yet the final form.

**Highlight:** the next major improvement should be introducing an explicit `action` field and typed event envelopes before more control or tool semantics accumulate.
