package ai.littleclaw.skill;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SkillLoaderTest {

    private final SkillLoader loader = new SkillLoader();

    @Test
    void parsesSkillsFromResourceDirectory() {
        List<Skill> skills = loader.load("src/main/resources/skills");
        assertFalse(skills.isEmpty());
        Skill weather = skills.stream().filter(skill -> skill.name().equals("weather")).findFirst().orElseThrow();
        assertEquals("1.0.0", weather.version());
        assertEquals(List.of("weather", "forecast", "temperature"), weather.triggers());
    }
}
