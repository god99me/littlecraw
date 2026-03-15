package ai.littleclaw.api;

import ai.littleclaw.chat.ChatControlResponse;
import ai.littleclaw.chat.ChatRequest;
import ai.littleclaw.chat.ChatResponse;
import ai.littleclaw.chat.ChatService;
import ai.littleclaw.logging.TimedOperation;
import ai.littleclaw.session.ActiveRequestRegistry;
import ai.littleclaw.skill.SkillRegistry;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/v1")
public class ChatController {

    private final ChatService chatService;
    private final SkillRegistry skillRegistry;
    private final ActiveRequestRegistry activeRequestRegistry;

    public ChatController(ChatService chatService, SkillRegistry skillRegistry, ActiveRequestRegistry activeRequestRegistry) {
        this.chatService = chatService;
        this.skillRegistry = skillRegistry;
        this.activeRequestRegistry = activeRequestRegistry;
    }

    @TimedOperation("chat.complete")
    @PostMapping(path = "/chat/completions", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ChatResponse> complete(@Valid @RequestBody ChatRequest request,
                                       @RequestAttribute(RequestContextFilter.ATTRIBUTE) RequestContext requestContext) {
        return chatService.complete(request, requestContext);
    }

    @TimedOperation("chat.stream")
    @PostMapping(path = "/chat/completions/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<?>> stream(@Valid @RequestBody ChatRequest request,
                                           @RequestAttribute(RequestContextFilter.ATTRIBUTE) RequestContext requestContext) {
        return chatService.stream(request, requestContext).cast(ServerSentEvent.class);
    }

    @PostMapping(path = "/chat/interrupt/{requestId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ChatControlResponse> interrupt(@PathVariable String requestId) {
        activeRequestRegistry.cancel(requestId);
        return Mono.just(new ChatControlResponse("handled", requestId, "Interrupt accepted.", Map.of("action", "interrupt", "targetRequestId", requestId)));
    }

    @GetMapping(path = "/skills", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> skills() {
        return Map.of(
                "count", skillRegistry.all().size(),
                "skills", skillRegistry.all()
        );
    }

    @GetMapping(path = "/health", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> health() {
        return Map.of("status", "ok", "service", "littleclaw");
    }
}
