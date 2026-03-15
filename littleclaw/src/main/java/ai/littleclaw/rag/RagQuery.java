package ai.littleclaw.rag;

public record RagQuery(
        String query,
        int topK
) {
}
