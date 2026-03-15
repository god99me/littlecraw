# LittleClaw API Contract v1

This document freezes the wire-level semantics that current clients can rely on.

## Request Headers

- `X-Request-Id` - optional caller-provided id; server generates one if absent
- `X-Tenant-Id` - optional tenant selector; when auth client credentials are configured this may be injected or validated server-side
- `X-Channel` - optional rendering hint such as `api`, `feishu`, or `discord`
- `X-API-Key` - required when `littleclaw.auth.enabled=true`

## Chat Request Fields

- `action` - `COMPLETE`, `STREAM`, `COMMAND`, `STOP`, `REGENERATE`
- `messages` - ordered chat messages
- `stream` - boolean stream hint
- `conversationId` - logical conversation key for continuation and regenerate lookup
- `requestId` - request id used for tracing and interrupt semantics
- `parentMessageId` - prior response id for threaded follow-up and stop flows
- `regenerateFromResponseId` - explicit response id to regenerate from
- `interruptRequestId` - explicit request id to stop

## Chat Response Fields

- `id` - server-generated response id
- `createdAt` - server timestamp
- `model` - provider/model identifier
- `activeSkills` - matched skills for the request
- `content` - rendered text
- `requestId` - caller or server request id
- `conversationId` - normalized conversation id
- `parentMessageId` - referenced response id when present
- `finishReason` - `completed`, `interrupted`, `stopped`, `control`, `not_found`
- `status` - `ok`, `error`, `interrupted`
- `errorCode` - stable machine-readable code or `null`
- `metadata` - additive metadata bag

## SSE Event Types

- `chunk` - incremental delta payload with `done=false`
- `done` - terminal payload with `done=true` and `status=ok`
- `interrupted` - terminal payload with `done=true` and `status=interrupted`
- `control` - command response payload

## Stable Error Codes

- `conversation_turn_not_found`
- `request_interrupted`
- `control_request_ignored`
- `missing_interrupt_target`
- `tenant_rate_limited`
- `global_stream_limit_exceeded`
- `tenant_stream_limit_exceeded`
- `provider_not_configured`
- `provider_auth_failed`
- `provider_rate_limited`
- `provider_upstream_failed`
- `provider_request_rejected`
- `provider_payload_decode_failed`

## Tenant Policy Metadata

Successful responses include `metadata.tenantPolicy` with the effective limits used for the request:

- `tenantId`
- `tenantName`
- `maxRequestsPerWindow`
- `maxActiveStreamsPerTenant`
- `maxInputChars`
- `maxMaxTokens`
