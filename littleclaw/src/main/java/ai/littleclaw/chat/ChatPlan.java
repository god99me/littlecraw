package ai.littleclaw.chat;

import ai.littleclaw.channel.ChannelRequest;
import ai.littleclaw.mcp.McpToolDescriptor;
import ai.littleclaw.rag.RagSnippet;
import ai.littleclaw.skill.Skill;

import java.util.List;

public record ChatPlan(
        String systemPrompt,
        String latestUserMessage,
        List<ChatMessage> messages,
        List<Skill> skills,
        List<RagSnippet> ragSnippets,
        List<McpToolDescriptor> mcpTools,
        ChannelRequest channel,
        Integer maxTokens,
        Double temperature
) {
}
