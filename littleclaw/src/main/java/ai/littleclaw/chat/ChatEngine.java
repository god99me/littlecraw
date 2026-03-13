package ai.littleclaw.chat;

import reactor.core.publisher.Flux;

public interface ChatEngine {

    Flux<String> stream(ChatPlan plan);
}
