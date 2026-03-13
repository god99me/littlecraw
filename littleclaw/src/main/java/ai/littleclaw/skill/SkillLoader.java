package ai.littleclaw.skill;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class SkillLoader {

    private static final Pattern FRONT_MATTER = Pattern.compile("^---\\s*(.*?)\\s*---\\s*(.*)$", Pattern.DOTALL);
    private static final Pattern KEY_VALUE = Pattern.compile("^([A-Za-z0-9_-]+):\\s*(.*)$");

    public List<Skill> load(String path) {
        String location = "file:" + path + "/**/SKILL.md";
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver().getResources(location);
            List<Skill> skills = new ArrayList<>();
            for (Resource resource : resources) {
                if (!resource.exists()) {
                    continue;
                }
                String raw = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                Skill skill = parse(raw, resource.getURI().toString());
                if (skill != null) {
                    skills.add(skill);
                }
            }
            return skills;
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    private Skill parse(String raw, String source) {
        Matcher matcher = FRONT_MATTER.matcher(raw);
        if (!matcher.find()) {
            return null;
        }
        Map<String, Object> metadata = FrontMatterParser.parse(matcher.group(1));
        String name = asString(metadata.get("name"), "unknown");
        String version = asString(metadata.get("version"), "0.0.0");
        String description = asString(metadata.get("description"), "");
        List<String> triggers = asStringList(metadata.get("triggers"));
        String body = matcher.group(2).trim();
        return new Skill(name, version, description, triggers, body, source);
    }

    private String asString(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String string = String.valueOf(value).trim();
        return StringUtils.hasText(string) ? string : fallback;
    }

    @SuppressWarnings("unchecked")
    private List<String> asStringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(item -> String.valueOf(item).toLowerCase(Locale.ROOT)).toList();
        }
        if (value == null) {
            return List.of();
        }
        return List.of(String.valueOf(value).toLowerCase(Locale.ROOT));
    }

    static class FrontMatterParser {

        static Map<String, Object> parse(String input) {
            java.util.LinkedHashMap<String, Object> values = new java.util.LinkedHashMap<>();
            String currentListKey = null;
            List<String> currentList = null;
            for (String rawLine : input.split("\\R")) {
                String line = rawLine.stripTrailing();
                if (!StringUtils.hasText(line)) {
                    continue;
                }
                Matcher matcher = KEY_VALUE.matcher(line.trim());
                if (matcher.matches()) {
                    currentListKey = null;
                    currentList = null;
                    String key = matcher.group(1).trim();
                    String value = matcher.group(2).trim();
                    if (value.isEmpty()) {
                        currentListKey = key;
                        currentList = new ArrayList<>();
                        values.put(key, currentList);
                    } else {
                        values.put(key, trimQuotes(value));
                    }
                    continue;
                }
                if (currentListKey != null && line.trim().startsWith("- ")) {
                    currentList.add(trimQuotes(line.trim().substring(2).trim()));
                }
            }
            return values;
        }

        private static String trimQuotes(String value) {
            if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
                return value.substring(1, value.length() - 1);
            }
            return value;
        }
    }
}
