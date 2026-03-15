package ai.littleclaw.chat;

import java.util.Map;

public record ChatControlResponse(
        String status,
        String requestId,
        String message,
        Map<String, Object> metadata
) {
}
