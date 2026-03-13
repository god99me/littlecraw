package ai.littleclaw.provider;

import ai.littleclaw.skill.Skill;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public class StubChatProvider implements ChatProvider {

    @Override
    public String id() {
        return "stub-provider";
    }

    @Override
    public Flux<ProviderChunk> stream(ProviderRequest request) {
        StringBuilder out = new StringBuilder();
        out.append("LittleClaw response:\n");
        if (!request.skills().isEmpty()) {
            out.append("Matched skills: ")
                    .append(String.join(", ", request.skills().stream().map(Skill::name).toList()))
                    .append("\n\n");
        }
        out.append("System prompt: ").append(request.systemPrompt()).append("\n");
        out.append("Messages: ").append(request.messages().size()).append("\n");
        out.append("This provider is a deterministic placeholder. Replace it with an async OpenAI, Claude, or vLLM adapter while keeping the transport layer unchanged.\n");
        return Flux.fromArray(out.toString().split("(?<=\\n)"))
                .map(chunk -> new ProviderChunk(chunk, false))
                .concatWithValues(new ProviderChunk("", true));
    }
}
