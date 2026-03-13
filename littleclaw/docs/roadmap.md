# Roadmap

## Night 1

- [x] Create WebFlux chatbot skeleton
- [x] Add SSE endpoint
- [x] Add local skill loading
- [x] Add provider abstraction
- [x] Add initial tests and docs
- [ ] Compile and run locally once Java/Maven are available

## Next implementation wave

- [ ] OpenAI-compatible provider adapter
- [ ] Redis-backed rate limiting
- [ ] API key / JWT auth filter
- [ ] tenant quotas and request cost controls
- [ ] k6 or Gatling benchmark scripts
- [ ] Micrometer metrics
- [ ] structured error model

## Production wave

- [ ] multi-agent orchestration over queue/workflow engine
- [ ] skill hot reload and cache invalidation
- [ ] tool policy engine and outbound allowlists
- [ ] persistent conversation memory
- [ ] blue/green provider failover
- [ ] admin control plane
