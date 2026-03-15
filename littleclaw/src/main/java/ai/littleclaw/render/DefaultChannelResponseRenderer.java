package ai.littleclaw.render;

import ai.littleclaw.chat.ChatChunk;
import ai.littleclaw.chat.ChatResponse;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class DefaultChannelResponseRenderer implements ChannelResponseRenderer {

    private final RenderPolicyRegistry renderPolicyRegistry;

    public DefaultChannelResponseRenderer(RenderPolicyRegistry renderPolicyRegistry) {
        this.renderPolicyRegistry = renderPolicyRegistry;
    }

    @Override
    public ChatResponse render(ChatResponse response, String requestChannel) {
        RenderPolicy policy = renderPolicyRegistry.resolve(requestChannel);
        Map<String, Object> metadata = new LinkedHashMap<>(response.metadata());
        metadata.put("renderedFor", requestChannel);
        metadata.put("renderPolicy", policy.channel());
        metadata.put("supportsCards", policy.supportsCards());
        metadata.put("supportsThreads", policy.supportsThreads());
        String content = applyPolicy(response.content(), policy);
        return new ChatResponse(
                response.id(),
                response.createdAt(),
                response.model(),
                response.activeSkills(),
                content,
                response.requestId(),
                response.conversationId(),
                response.parentMessageId(),
                response.finishReason(),
                metadata
        );
    }

    @Override
    public ChatChunk render(ChatChunk chunk, String requestChannel) {
        RenderPolicy policy = renderPolicyRegistry.resolve(requestChannel);
        Map<String, Object> metadata = new LinkedHashMap<>(chunk.metadata());
        metadata.put("renderedFor", requestChannel);
        metadata.put("renderPolicy", policy.channel());
        String delta = applyPolicy(chunk.delta(), policy);
        return new ChatChunk(
                chunk.id(),
                chunk.createdAt(),
                chunk.model(),
                chunk.activeSkills(),
                delta,
                chunk.done(),
                chunk.requestId(),
                chunk.conversationId(),
                chunk.parentMessageId(),
                chunk.finishReason(),
                metadata
        );
    }

    private String applyPolicy(String content, RenderPolicy policy) {
        if (content == null || content.isBlank() || policy.preservePlainText()) {
            return content;
        }
        return policy.prefix() + content;
    }
}
