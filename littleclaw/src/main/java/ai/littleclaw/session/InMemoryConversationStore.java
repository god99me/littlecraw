package ai.littleclaw.session;

import ai.littleclaw.chat.ChatMessage;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryConversationStore implements ConversationStore {

    private final Map<String, ConversationTurn> turnsByResponseId = new ConcurrentHashMap<>();
    private final Map<String, String> latestResponseByConversation = new ConcurrentHashMap<>();

    @Override
    public void save(String conversationId, String responseId, String requestId, List<ChatMessage> requestMessages, String responseContent) {
        List<ChatMessage> transcriptMessages = buildTranscript(requestMessages, responseContent);
        turnsByResponseId.put(responseId, new ConversationTurn(
                responseId,
                requestId,
                List.copyOf(requestMessages),
                transcriptMessages
        ));
        if (conversationId != null && !conversationId.isBlank()) {
            latestResponseByConversation.put(conversationId, responseId);
        }
    }

    private List<ChatMessage> buildTranscript(List<ChatMessage> requestMessages, String responseContent) {
        List<ChatMessage> transcript = new java.util.ArrayList<>(requestMessages);
        if (responseContent != null && !responseContent.isBlank()) {
            transcript.add(new ChatMessage("assistant", responseContent));
        }
        return List.copyOf(transcript);
    }

    @Override
    public ConversationTurn findByResponseId(String responseId) {
        return turnsByResponseId.get(responseId);
    }

    @Override
    public ConversationTurn latestForConversation(String conversationId) {
        String responseId = latestResponseByConversation.get(conversationId);
        return responseId == null ? null : turnsByResponseId.get(responseId);
    }
}
