package ai.littleclaw.session;

import ai.littleclaw.chat.ChatMessage;
import ai.littleclaw.config.LittleClawProperties;
import ai.littleclaw.observability.ChatMetrics;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
@ConditionalOnBean(ReactiveStringRedisTemplate.class)
@ConditionalOnProperty(prefix = "littleclaw.admission", name = "redis-enabled", havingValue = "true")
public class RedisConversationStore implements ConversationStore {

    private static final TypeReference<ConversationTurnPayload> TURN_PAYLOAD = new TypeReference<>() {
    };

    private final ReactiveStringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final ConversationTranscriptPolicy transcriptPolicy;
    private final LittleClawProperties properties;
    private final ChatMetrics metrics;

    public RedisConversationStore(ReactiveStringRedisTemplate redis,
                                  ObjectMapper objectMapper,
                                  ConversationTranscriptPolicy transcriptPolicy,
                                  LittleClawProperties properties,
                                  ChatMetrics metrics) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.transcriptPolicy = transcriptPolicy;
        this.properties = properties;
        this.metrics = metrics;
    }

    @Override
    public void save(String conversationId, String responseId, String requestId, List<ChatMessage> requestMessages, String responseContent) {
        try {
            ConversationTurn turn = transcriptPolicy.buildTurn(responseId, requestId, requestMessages, responseContent);
            String payload = objectMapper.writeValueAsString(new ConversationTurnPayload(
                    turn.requestId(),
                    turn.requestMessages(),
                    turn.transcriptMessages()
            ));
            Duration ttl = Duration.ofSeconds(properties.getSession().getConversationTtlSeconds());
            redis.opsForValue().set(turnKey(responseId), payload, ttl).block();
            if (conversationId != null && !conversationId.isBlank()) {
                redis.opsForValue().set(latestKey(conversationId), responseId, ttl).block();
            }
            metrics.recordConversationStore("save", "success");
        } catch (Exception exception) {
            metrics.recordConversationStore("save", "error");
            throw new IllegalStateException("Failed to persist conversation turn.", exception);
        }
    }

    @Override
    public ConversationTurn findByResponseId(String responseId) {
        try {
            String payload = redis.opsForValue().get(turnKey(responseId)).block();
            if (payload == null) {
                metrics.recordConversationStore("find_by_response", "miss");
                return null;
            }
            ConversationTurnPayload turn = objectMapper.readValue(payload, TURN_PAYLOAD);
            metrics.recordConversationStore("find_by_response", "hit");
            return new ConversationTurn(responseId, turn.requestId(), turn.requestMessages(), turn.transcriptMessages());
        } catch (Exception exception) {
            metrics.recordConversationStore("find_by_response", "error");
            throw new IllegalStateException("Failed to load conversation turn.", exception);
        }
    }

    @Override
    public ConversationTurn latestForConversation(String conversationId) {
        String responseId = redis.opsForValue().get(latestKey(conversationId)).block();
        if (responseId == null) {
            metrics.recordConversationStore("latest_for_conversation", "miss");
            return null;
        }
        ConversationTurn turn = findByResponseId(responseId);
        metrics.recordConversationStore("latest_for_conversation", turn == null ? "miss" : "hit");
        return turn;
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
