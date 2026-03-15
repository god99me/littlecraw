package ai.littleclaw.session;

import ai.littleclaw.chat.ChatMessage;

import java.util.List;

public interface ConversationStore {

    void save(String conversationId, String responseId, String requestId, List<ChatMessage> requestMessages, String responseContent);

    ConversationTurn findByResponseId(String responseId);

    ConversationTurn latestForConversation(String conversationId);
}
