package ai.littleclaw.rag;

public record RagSnippet(
        String source,
        String title,
        String content,
        int score
) {
}
