package ai.littleclaw.provider;

import ai.littleclaw.config.LittleClawProperties;
import ai.littleclaw.mcp.McpToolDescriptor;
import ai.littleclaw.rag.RagSnippet;
import ai.littleclaw.skill.Skill;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
@ConditionalOnProperty(prefix = "littleclaw.provider", name = "type", havingValue = "stub", matchIfMissing = true)
public class StubChatProvider implements ChatProvider {

    private final LittleClawProperties properties;

    public StubChatProvider(LittleClawProperties properties) {
        this.properties = properties;
    }

    @Override
    public String id() {
        return "stub-provider";
    }

    @Override
    public Flux<ProviderChunk> stream(ProviderRequest request) {
        StringBuilder out = new StringBuilder();
        out.append("LittleClaw response:\n");
        if (request.channel() != null && request.channel().channel() != null) {
            out.append("Channel: ").append(request.channel().channel()).append("\n");
        }
        if (!request.skills().isEmpty()) {
            out.append("Matched skills: ")
                    .append(String.join(", ", request.skills().stream().map(Skill::name).toList()))
                    .append("\n");
        }
        if (!request.ragSnippets().isEmpty()) {
            out.append("RAG hits: ")
                    .append(String.join(", ", request.ragSnippets().stream().map(RagSnippet::title).toList()))
                    .append("\n");
        }
        if (!request.mcpTools().isEmpty()) {
            out.append("MCP tools: ")
                    .append(String.join(", ", request.mcpTools().stream().map(McpToolDescriptor::tool).toList()))
                    .append("\n");
        }
        out.append("System prompt: ").append(request.systemPrompt()).append("\n");
        out.append("Messages: ").append(request.messages().size()).append("\n");
        out.append("Provider type: ").append(properties.getProvider().getType()).append("\n");
        out.append("This provider is a deterministic placeholder. Replace it with an async provider that keeps channel, RAG, and MCP context wiring unchanged.\n");
        return Flux.fromArray(out.toString().split("(?<=\\n)"))
                .map(chunk -> new ProviderChunk(chunk, false))
                .concatWithValues(new ProviderChunk("", true));
    }
}
