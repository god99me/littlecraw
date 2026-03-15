package ai.littleclaw.session;

import ai.littleclaw.chat.ChatMessage;
import ai.littleclaw.config.LittleClawProperties;
import ai.littleclaw.observability.ChatMetrics;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryConversationStore implements ConversationStore {

    private final Map<String, StoredTurn> turnsByResponseId = new ConcurrentHashMap<>();
    private final Map<String, String> latestResponseByConversation = new ConcurrentHashMap<>();
    private final ConversationTranscriptPolicy transcriptPolicy;
    private final LittleClawProperties properties;
    private final ChatMetrics metrics;

    public InMemoryConversationStore(ConversationTranscriptPolicy transcriptPolicy, LittleClawProperties properties, ChatMetrics metrics) {
        this.transcriptPolicy = transcriptPolicy;
        this.properties = properties;
        this.metrics = metrics;
    }

    @Override
    public void save(String conversationId, String responseId, String requestId, List<ChatMessage> requestMessages, String responseContent) {
        ConversationTurn turn = transcriptPolicy.buildTurn(responseId, requestId, requestMessages, responseContent);
        turnsByResponseId.put(responseId, new StoredTurn(turn, Instant.now().plusSeconds(properties.getSession().getConversationTtlSeconds())));
        if (conversationId != null && !conversationId.isBlank()) {
            latestResponseByConversation.put(conversationId, responseId);
        }
        metrics.recordConversationStore("save", "success");
    }

    @Override
    public ConversationTurn findByResponseId(String responseId) {
        StoredTurn storedTurn = turnsByResponseId.get(responseId);
        if (storedTurn == null) {
            metrics.recordConversationStore("find_by_response", "miss");
            return null;
        }
        if (storedTurn.expiresAt().isBefore(Instant.now())) {
            turnsByResponseId.remove(responseId);
            metrics.recordConversationStore("find_by_response", "expired");
            return null;
        }
        metrics.recordConversationStore("find_by_response", "hit");
        return storedTurn.turn();
    }

    @Override
    public ConversationTurn latestForConversation(String conversationId) {
        String responseId = latestResponseByConversation.get(conversationId);
        if (responseId == null) {
            metrics.recordConversationStore("latest_for_conversation", "miss");
            return null;
        }
        ConversationTurn turn = findByResponseId(responseId);
        if (turn == null) {
            latestResponseByConversation.remove(conversationId, responseId);
            metrics.recordConversationStore("latest_for_conversation", "expired");
            return null;
        }
        metrics.recordConversationStore("latest_for_conversation", "hit");
        return turn;
    }

    private record StoredTurn(ConversationTurn turn, Instant expiresAt) {
    }
}
