package ai.littleclaw.session;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Primary
public class CompositeConversationStore implements ConversationStore {

    private final ConversationStore delegate;

    public CompositeConversationStore(InMemoryConversationStore fallback, ObjectProvider<RedisConversationStore> redisConversationStore) {
        this.delegate = redisConversationStore.getIfAvailable(() -> fallback);
    }

    @Override
    public void save(String conversationId, String responseId, String requestId, List<ai.littleclaw.chat.ChatMessage> requestMessages, String responseContent) {
        delegate.save(conversationId, responseId, requestId, requestMessages, responseContent);
    }

    @Override
    public ConversationTurn findByResponseId(String responseId) {
        return delegate.findByResponseId(responseId);
    }

    @Override
    public ConversationTurn latestForConversation(String conversationId) {
        return delegate.latestForConversation(conversationId);
    }
}
