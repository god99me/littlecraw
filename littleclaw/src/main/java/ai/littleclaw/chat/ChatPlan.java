package ai.littleclaw.chat;

import ai.littleclaw.skill.Skill;

import java.util.List;

public record ChatPlan(
        String systemPrompt,
        String latestUserMessage,
        List<ChatMessage> messages,
        List<Skill> skills
) {
}
