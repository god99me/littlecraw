package ai.littleclaw.provider;

import ai.littleclaw.chat.ChatMessage;
import ai.littleclaw.skill.Skill;

import java.util.List;

public record ProviderRequest(
        String systemPrompt,
        List<ChatMessage> messages,
        List<Skill> skills,
        Integer maxTokens,
        Double temperature
) {
}
