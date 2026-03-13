package ai.littleclaw.api;

import ai.littleclaw.chat.ChatRequest;
import ai.littleclaw.chat.ChatResponse;
import ai.littleclaw.chat.ChatService;
import ai.littleclaw.skill.SkillRegistry;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.Map;

@RestController
@RequestMapping("/v1")
public class ChatController {

    private final ChatService chatService;
    private final SkillRegistry skillRegistry;

    public ChatController(ChatService chatService, SkillRegistry skillRegistry) {
        this.chatService = chatService;
        this.skillRegistry = skillRegistry;
    }

    @PostMapping(path = "/chat/completions", produces = MediaType.APPLICATION_JSON_VALUE)
    public ChatResponse complete(@Valid @RequestBody ChatRequest request) {
        return chatService.complete(request);
    }

    @PostMapping(path = "/chat/completions/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<?>> stream(@Valid @RequestBody ChatRequest request) {
        return chatService.stream(request).cast(ServerSentEvent.class);
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
