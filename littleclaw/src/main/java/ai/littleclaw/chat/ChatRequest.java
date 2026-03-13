package ai.littleclaw.chat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ChatRequest(
        @NotEmpty List<@Valid ChatMessage> messages,
        boolean stream,
        Integer maxTokens,
        Double temperature
) {
}
