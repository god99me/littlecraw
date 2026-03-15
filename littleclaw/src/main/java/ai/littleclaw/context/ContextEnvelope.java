package ai.littleclaw.context;

import ai.littleclaw.channel.ChannelDescriptor;
import ai.littleclaw.mcp.McpToolDescriptor;
import ai.littleclaw.rag.RagSnippet;
import ai.littleclaw.skill.Skill;

import java.util.List;

public record ContextEnvelope(
        String assembledSystemPrompt,
        ChannelDescriptor channel,
        List<Skill> matchedSkills,
        List<RagSnippet> ragSnippets,
        List<McpToolDescriptor> mcpTools
) {
}
