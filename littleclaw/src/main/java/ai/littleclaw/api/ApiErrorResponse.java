package ai.littleclaw.api;

import java.time.Instant;
import java.util.Map;

public record ApiErrorResponse(
        Instant timestamp,
        String error,
        String message,
        String requestId,
        String tenantId,
        Map<String, Object> details
) {
}
