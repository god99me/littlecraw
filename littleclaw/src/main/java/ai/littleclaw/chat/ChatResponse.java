package ai.littleclaw.chat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ChatResponse(
        String id,
        Instant createdAt,
        String model,
        List<String> activeSkills,
        String content,
        String requestId,
        String conversationId,
        String parentMessageId,
        String finishReason,
        String status,
        String errorCode,
        Map<String, Object> metadata
) {
}
