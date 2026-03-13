package ai.littleclaw.chat;

import ai.littleclaw.provider.ChatProvider;
import ai.littleclaw.provider.ProviderChunk;
import ai.littleclaw.provider.ProviderRequest;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public class HeuristicChatEngine implements ChatEngine {

    private final ChatProvider chatProvider;

    public HeuristicChatEngine(ChatProvider chatProvider) {
        this.chatProvider = chatProvider;
    }

    @Override
    public Flux<String> stream(ChatPlan plan) {
        ProviderRequest request = new ProviderRequest(
                plan.systemPrompt(),
                plan.messages(),
                plan.skills(),
                null,
                null
        );
        return chatProvider.stream(request)
                .filter(chunk -> !chunk.done())
                .map(ProviderChunk::delta);
    }
}
