package ai.littleclaw.rag;

import reactor.core.publisher.Mono;

import java.util.List;

public interface RagService {

    Mono<List<RagSnippet>> retrieve(RagQuery query);
}
