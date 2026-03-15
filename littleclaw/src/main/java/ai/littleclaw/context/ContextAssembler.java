package ai.littleclaw.context;

import ai.littleclaw.channel.ChannelDescriptor;
import ai.littleclaw.channel.ChannelRegistry;
import ai.littleclaw.channel.ChannelRequest;
import ai.littleclaw.mcp.McpRegistry;
import ai.littleclaw.mcp.McpToolDescriptor;
import ai.littleclaw.rag.RagQuery;
import ai.littleclaw.rag.RagService;
import ai.littleclaw.rag.RagSnippet;
import ai.littleclaw.skill.Skill;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

@Component
public class ContextAssembler {

    private final RagService ragService;
    private final McpRegistry mcpRegistry;
    private final ChannelRegistry channelRegistry;

    public ContextAssembler(RagService ragService, McpRegistry mcpRegistry, ChannelRegistry channelRegistry) {
        this.ragService = ragService;
        this.mcpRegistry = mcpRegistry;
        this.channelRegistry = channelRegistry;
    }

    public Mono<ContextEnvelope> assemble(String baseSystemPrompt,
                                          String latestUserMessage,
                                          List<Skill> matchedSkills,
                                          ChannelRequest channelRequest,
                                          int ragTopK) {
        Optional<ChannelDescriptor> channel = channelRegistry.find(channelRequest == null ? null : channelRequest.channel());
        List<McpToolDescriptor> mcpTools = mcpRegistry.availableTools();
        return ragService.retrieve(new RagQuery(latestUserMessage, ragTopK))
                .map(ragSnippets -> new ContextEnvelope(
                        buildSystemPrompt(baseSystemPrompt, matchedSkills, channel.orElse(null), ragSnippets, mcpTools),
                        channel.orElse(null),
                        matchedSkills,
                        ragSnippets,
                        mcpTools
                ));
    }

    private String buildSystemPrompt(String baseSystemPrompt,
                                     List<Skill> matchedSkills,
                                     ChannelDescriptor channel,
                                     List<RagSnippet> ragSnippets,
                                     List<McpToolDescriptor> mcpTools) {
        StringBuilder builder = new StringBuilder(baseSystemPrompt);
        if (channel != null) {
            builder.append("\n\nChannel context:\n");
            builder.append("- channel: ").append(channel.name()).append("\n");
            builder.append("- capabilities: ").append(channel.capabilities()).append("\n");
        }
        if (!matchedSkills.isEmpty()) {
            builder.append("\nMatched skills:\n");
            matchedSkills.forEach(skill -> builder.append("- ").append(skill.name()).append(": ").append(skill.description()).append("\n"));
        }
        if (!ragSnippets.isEmpty()) {
            builder.append("\nRetrieved context:\n");
            ragSnippets.forEach(snippet -> builder.append("- ").append(snippet.title()).append(": ").append(snippet.content()).append("\n"));
        }
        if (!mcpTools.isEmpty()) {
            builder.append("\nAvailable MCP tools:\n");
            mcpTools.forEach(tool -> builder.append("- ").append(tool.server()).append("/").append(tool.tool()).append(" via ").append(tool.transport()).append("\n"));
        }
        return builder.toString();
    }
}
