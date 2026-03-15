package ai.littleclaw.render;

public record RenderPolicy(
        String channel,
        boolean preservePlainText,
        String prefix,
        boolean supportsCards,
        boolean supportsThreads
) {
}
