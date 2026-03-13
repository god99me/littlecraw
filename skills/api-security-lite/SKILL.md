---
name: api-security-lite
description: Secure API defaults for auth, validation, rate limiting, and secret handling in chatbot backends.
version: 0.1.0
triggers:
  - security
  - auth
  - rate limit
  - validation
  - jwt
  - api
---

# API Security Lite

Use this skill when designing or reviewing API security.

## Defaults

- Validate every request body against a schema
- Sanitize error responses; never leak provider internals
- Use short-lived access tokens and rotated refresh tokens
- Enforce per-IP and per-tenant rate limits
- Keep secrets only in env vars or secret stores
- Add audit logs for auth failures and admin actions

## For chatbot APIs

- Separate user auth from provider credentials
- Never send raw tool or stack traces to clients
- Limit max input chars, max turns, and tool invocations per request
- Add allowlists for outbound tool/network access where possible
