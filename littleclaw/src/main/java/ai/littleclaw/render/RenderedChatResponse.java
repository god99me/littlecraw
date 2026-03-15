package ai.littleclaw.render;

import java.util.Map;

public record RenderedChatResponse(
        String content,
        Map<String, Object> metadata
) {
}
