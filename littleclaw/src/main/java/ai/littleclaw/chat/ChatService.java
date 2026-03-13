package ai.littleclaw.chat;

import ai.littleclaw.config.LittleClawProperties;
import ai.littleclaw.skill.Skill;
import ai.littleclaw.skill.SkillMatch;
import ai.littleclaw.skill.SkillRegistry;
import jakarta.validation.ValidationException;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ChatService {

    private final SkillRegistry skillRegistry;
    private final ChatEngine chatEngine;
    private final LittleClawProperties properties;

    public ChatService(SkillRegistry skillRegistry, ChatEngine chatEngine, LittleClawProperties properties) {
        this.skillRegistry = skillRegistry;
        this.chatEngine = chatEngine;
        this.properties = properties;
    }

    public ChatResponse complete(ChatRequest request) {
        ChatSession session = createSession(request);
        String content = chatEngine.stream(session.plan()).collectList().map(parts -> String.join("", parts)).block();
        return new ChatResponse(session.id(), session.createdAt(), "littleclaw-heuristic", session.skillNames(), content);
    }

    public Flux<ServerSentEvent<ChatChunk>> stream(ChatRequest request) {
        ChatSession session = createSession(request);
        Flux<String> tokens = chatEngine.stream(session.plan());
        if (properties.getStream().getTokenDelayMs() > 0) {
            tokens = tokens.delayElements(Duration.ofMillis(properties.getStream().getTokenDelayMs()));
        }
        return tokens.map(delta -> ServerSentEvent.<ChatChunk>builder()
                        .event("chunk")
                        .id(session.id())
                        .data(new ChatChunk(session.id(), session.createdAt(), "littleclaw-heuristic", session.skillNames(), delta, false))
                        .build())
                .concatWithValues(ServerSentEvent.<ChatChunk>builder()
                        .event("done")
                        .id(session.id())
                        .data(new ChatChunk(session.id(), session.createdAt(), "littleclaw-heuristic", session.skillNames(), "", true))
                        .build());
    }

    private ChatSession createSession(ChatRequest request) {
        validate(request);
        String latestUserMessage = request.messages().get(request.messages().size() - 1).content();
        List<Skill> matchedSkills = skillRegistry.match(latestUserMessage, 4).stream().map(SkillMatch::skill).toList();
        ChatPlan plan = new ChatPlan(properties.getSystemPrompt(), latestUserMessage, request.messages(), matchedSkills);
        return new ChatSession(UUID.randomUUID().toString(), Instant.now(), plan, matchedSkills.stream().map(Skill::name).toList());
    }

    private void validate(ChatRequest request) {
        if (request.messages().size() > properties.getLimits().getMaxMessages()) {
            throw new ValidationException("Too many messages in request.");
        }
        int totalChars = request.messages().stream().map(ChatMessage::content).filter(StringUtils::hasText).mapToInt(String::length).sum();
        if (totalChars > properties.getLimits().getMaxInputChars()) {
            throw new ValidationException("Input exceeds configured size limit.");
        }
    }

    private record ChatSession(String id, Instant createdAt, ChatPlan plan, List<String> skillNames) {
    }
}
