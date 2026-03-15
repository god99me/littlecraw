package ai.littleclaw.session;

import ai.littleclaw.chat.ChatMessage;

import java.util.List;

public record ConversationTurn(
        String responseId,
        String requestId,
        List<ChatMessage> requestMessages,
        List<ChatMessage> transcriptMessages
) {
}
