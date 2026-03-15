# Regenerate and channel-aware rendering

## What changed

This iteration adds two product-facing capabilities that move the project beyond a raw transport skeleton:

- real regenerate flow based on stored conversation turns
- channel-aware response rendering behind a dedicated renderer boundary

## Regenerate flow

Regenerate is no longer only a protocol placeholder.

Current behavior:

1. completed turns are stored in an in-memory conversation store
2. regenerate requests resolve a previous turn by `regenerateFromResponseId` or latest conversation response
3. the original message list is replayed into a fresh completion request
4. the regenerated response links back through `parentMessageId`

Current limitations:

- storage is in-memory only
- no edit-diff or branch model yet
- no multi-version conversation tree yet

## Channel-aware rendering

Response shaping is now split from orchestration.

Instead of scattering channel-specific string handling inside `ChatService`, rendering happens through `ChannelResponseRenderer`.

Current behavior:

- API/default channels pass content through
- Feishu keeps plain content with channel metadata
- Discord can apply channel-specific formatting conventions
- response metadata records `renderedFor`

This is intentionally a starter renderer, but the boundary is the important part.

## Why this matters

These two additions reduce future refactor pain:

- regenerate now depends on stored conversation state instead of ad-hoc client retries
- channel customization now belongs to a renderer layer instead of the orchestration layer

## Recommended next steps

1. persist conversation turns in Redis or a database
2. add branch-aware regenerate semantics
3. add typed per-channel render policies for cards, markdown, attachments, and threads
4. add audit records for regenerate requests and interrupted runs
