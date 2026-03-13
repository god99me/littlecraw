package ai.littleclaw.skill;

import ai.littleclaw.config.LittleClawProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class SkillRegistry {

    private final SkillLoader skillLoader;
    private final LittleClawProperties properties;
    private final AtomicReference<List<Skill>> skills = new AtomicReference<>(List.of());

    public SkillRegistry(SkillLoader skillLoader, LittleClawProperties properties) {
        this.skillLoader = skillLoader;
        this.properties = properties;
    }

    @PostConstruct
    public void refresh() {
        List<Skill> loaded = skillLoader.load(properties.getSkills().getPath());
        if (loaded.isEmpty()) {
            loaded = skillLoader.load("src/main/resources/skills");
        }
        skills.set(List.copyOf(loaded));
    }

    public List<Skill> all() {
        return skills.get();
    }

    public List<SkillMatch> match(String text, int limit) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        return skills.get().stream()
                .map(skill -> new SkillMatch(skill, score(skill, normalized)))
                .filter(match -> match.score() > 0)
                .sorted(Comparator.comparingInt(SkillMatch::score).reversed())
                .limit(limit)
                .toList();
    }

    private int score(Skill skill, String normalized) {
        int score = 0;
        if (normalized.contains(skill.name().toLowerCase(Locale.ROOT))) {
            score += 10;
        }
        if (normalized.contains(skill.description().toLowerCase(Locale.ROOT))) {
            score += 3;
        }
        for (String trigger : skill.triggers()) {
            if (normalized.contains(trigger)) {
                score += 5;
            }
        }
        return score;
    }
}
