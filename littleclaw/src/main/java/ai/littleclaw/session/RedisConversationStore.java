package ai.littleclaw.session;

import ai.littleclaw.chat.ChatMessage;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnBean(ReactiveStringRedisTemplate.class)
@ConditionalOnProperty(prefix = "littleclaw.admission", name = "redis-enabled", havingValue = "true")
public class RedisConversationStore implements ConversationStore {

    private static final TypeReference<ConversationTurnPayload> TURN_PAYLOAD = new TypeReference<>() {
    };

    private final ReactiveStringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisConversationStore(ReactiveStringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(String conversationId, String responseId, String requestId, List<ChatMessage> requestMessages, String responseContent) {
        try {
            List<ChatMessage> transcriptMessages = new java.util.ArrayList<>(requestMessages);
            if (responseContent != null && !responseContent.isBlank()) {
                transcriptMessages.add(new ChatMessage("assistant", responseContent));
            }
            String payload = objectMapper.writeValueAsString(new ConversationTurnPayload(
                    requestId,
                    requestMessages,
                    transcriptMessages
            ));
            redis.opsForValue().set(turnKey(responseId), payload).block();
            if (conversationId != null && !conversationId.isBlank()) {
                redis.opsForValue().set(latestKey(conversationId), responseId).block();
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to persist conversation turn.", exception);
        }
    }

    @Override
    public ConversationTurn findByResponseId(String responseId) {
        try {
            String payload = redis.opsForValue().get(turnKey(responseId)).block();
            if (payload == null) {
                return null;
            }
            ConversationTurnPayload turn = objectMapper.readValue(payload, TURN_PAYLOAD);
            return new ConversationTurn(responseId, turn.requestId(), turn.requestMessages(), turn.transcriptMessages());
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to load conversation turn.", exception);
        }
    }

    @Override
    public ConversationTurn latestForConversation(String conversationId) {
        String responseId = redis.opsForValue().get(latestKey(conversationId)).block();
        return responseId == null ? null : findByResponseId(responseId);
    }

    private String turnKey(String responseId) {
        return "littleclaw:conversation:turn:" + responseId;
    }

    private String latestKey(String conversationId) {
        return "littleclaw:conversation:latest:" + conversationId;
    }

    private record ConversationTurnPayload(
            String requestId,
            List<ChatMessage> requestMessages,
            List<ChatMessage> transcriptMessages
    ) {
    }
}
