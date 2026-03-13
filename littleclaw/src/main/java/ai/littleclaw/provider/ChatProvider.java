package ai.littleclaw.provider;

import reactor.core.publisher.Flux;

public interface ChatProvider {

    String id();

    Flux<ProviderChunk> stream(ProviderRequest request);
}
