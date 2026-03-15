package ai.littleclaw.api;

public record RequestContext(
        String tenantId,
        String requestId,
        String channel
) {
}
