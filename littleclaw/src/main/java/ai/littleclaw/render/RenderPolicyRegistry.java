package ai.littleclaw.render;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class RenderPolicyRegistry {

    private final Map<String, RenderPolicy> policies = Map.of(
            "api", new RenderPolicy("api", true, "", false, false),
            "feishu", new RenderPolicy("feishu", true, "", true, false),
            "discord", new RenderPolicy("discord", false, "[discord] ", false, true),
            "slack", new RenderPolicy("slack", false, "[slack] ", false, true),
            "telegram", new RenderPolicy("telegram", true, "", false, false)
    );

    public RenderPolicy resolve(String channel) {
        if (channel == null || channel.isBlank()) {
            return policies.get("api");
        }
        return policies.getOrDefault(channel.toLowerCase(), policies.get("api"));
    }
}
