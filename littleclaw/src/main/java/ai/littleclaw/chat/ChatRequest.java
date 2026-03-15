package ai.littleclaw.chat;

import ai.littleclaw.channel.ChannelRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ChatRequest(
        ChatAction action,
        @NotEmpty List<@Valid ChatMessage> messages,
        boolean stream,
        Integer maxTokens,
        Double temperature,
        @Valid ChannelRequest channel,
        String conversationId,
        String requestId,
        String parentMessageId,
        String regenerateFromResponseId,
        String interruptRequestId
) {
}
