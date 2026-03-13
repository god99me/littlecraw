package ai.littleclaw.chat;

import java.time.Instant;
import java.util.List;

public record ChatChunk(
        String id,
        Instant createdAt,
        String model,
        List<String> activeSkills,
        String delta,
        boolean done
) {
}
