package ai.littleclaw.rag;

import ai.littleclaw.config.LittleClawProperties;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

@Component
public class LocalFilesystemRagService implements RagService {

    private final LittleClawProperties properties;

    public LocalFilesystemRagService(LittleClawProperties properties) {
        this.properties = properties;
    }

    @Override
    public Mono<List<RagSnippet>> retrieve(RagQuery query) {
        if (!properties.getRag().isEnabled() || query.query() == null || query.query().isBlank()) {
            return Mono.just(List.of());
        }
        return Mono.fromSupplier(() -> scan(query));
    }

    private List<RagSnippet> scan(RagQuery query) {
        List<RagSnippet> results = new ArrayList<>();
        String normalizedQuery = query.query().toLowerCase(Locale.ROOT);
        for (String includePath : properties.getRag().getIncludePaths()) {
            Path root = Path.of(includePath).normalize();
            if (!Files.exists(root)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(root)) {
                files.filter(Files::isRegularFile)
                        .filter(this::isIndexable)
                        .forEach(path -> maybeAdd(results, root, path, normalizedQuery));
            } catch (IOException ignored) {
                // Skip unreadable roots to keep the hot path resilient.
            }
        }
        return results.stream()
                .sorted(Comparator.comparingInt(RagSnippet::score).reversed())
                .limit(query.topK())
                .toList();
    }

    private boolean isIndexable(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".md") || name.endsWith(".txt") || name.endsWith(".json") || name.endsWith(".yml") || name.endsWith(".yaml");
    }

    private void maybeAdd(List<RagSnippet> results, Path root, Path path, String query) {
        try {
            String content = Files.readString(path);
            int score = score(content, query);
            if (score <= 0) {
                return;
            }
            String relative = root.relativize(path).toString();
            results.add(new RagSnippet(
                    path.toString(),
                    relative,
                    excerpt(content, query),
                    score
            ));
        } catch (IOException ignored) {
            // Skip individual files rather than failing the request.
        }
    }

    private int score(String content, String query) {
        String normalizedContent = content.toLowerCase(Locale.ROOT);
        int score = 0;
        for (String token : query.split("\\s+")) {
            if (token.length() < 2) {
                continue;
            }
            if (normalizedContent.contains(token)) {
                score++;
            }
        }
        return score;
    }

    private String excerpt(String content, String query) {
        String normalized = content.toLowerCase(Locale.ROOT);
        int index = normalized.indexOf(query.split("\\s+")[0]);
        if (index < 0) {
            return content.substring(0, Math.min(280, content.length()));
        }
        int start = Math.max(0, index - 80);
        int end = Math.min(content.length(), index + 200);
        return content.substring(start, end).replaceAll("\\s+", " ").trim();
    }
}
