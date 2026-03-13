package ai.littleclaw.skill;

import java.util.List;

public record Skill(
        String name,
        String version,
        String description,
        List<String> triggers,
        String body,
        String source
) {
}
