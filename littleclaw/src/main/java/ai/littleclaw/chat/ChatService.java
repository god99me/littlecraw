package ai.littleclaw.chat;

import ai.littleclaw.admission.AdmissionController;
import ai.littleclaw.admission.AdmissionRequest;
import ai.littleclaw.api.RequestContext;
import ai.littleclaw.command.CommandRouter;
import ai.littleclaw.config.LittleClawProperties;
import ai.littleclaw.context.ContextAssembler;
import ai.littleclaw.context.ContextEnvelope;
import ai.littleclaw.render.ChannelResponseRenderer;
import ai.littleclaw.skill.Skill;
import ai.littleclaw.skill.SkillMatch;
import ai.littleclaw.skill.SkillRegistry;
import ai.littleclaw.session.ActiveRequestRegistry;
import ai.littleclaw.session.ConversationStore;
import ai.littleclaw.session.ConversationTurn;
import jakarta.validation.ValidationException;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ChatService {

    private static final Set<String> ALLOWED_ROLES = Set.of("system", "user", "assistant", "tool");

    private final SkillRegistry skillRegistry;
    private final ChatEngine chatEngine;
    private final LittleClawProperties properties;
    private final AdmissionController admissionController;
    private final ContextAssembler contextAssembler;
    private final CommandRouter commandRouter;
    private final ActiveRequestRegistry activeRequestRegistry;
    private final ConversationStore conversationStore;
    private final ChannelResponseRenderer channelResponseRenderer;

    public ChatService(SkillRegistry skillRegistry,
                       ChatEngine chatEngine,
                       LittleClawProperties properties,
                       AdmissionController admissionController,
                       ContextAssembler contextAssembler,
                       CommandRouter commandRouter,
                       ActiveRequestRegistry activeRequestRegistry,
                       ConversationStore conversationStore,
                       ChannelResponseRenderer channelResponseRenderer) {
        this.skillRegistry = skillRegistry;
        this.chatEngine = chatEngine;
        this.properties = properties;
        this.admissionController = admissionController;
        this.contextAssembler = contextAssembler;
        this.commandRouter = commandRouter;
        this.activeRequestRegistry = activeRequestRegistry;
        this.conversationStore = conversationStore;
        this.channelResponseRenderer = channelResponseRenderer;
    }

    public Mono<ChatResponse> complete(ChatRequest request, RequestContext requestContext) {
        ChatRequest normalized = normalize(request, requestContext);
        if (normalized.action() == ChatAction.STOP) {
            return commandRouter.route(normalized).map(control -> renderResponse(toControlResponse(normalized, control, "stopped"), requestContext));
        }
        if (normalized.action() == ChatAction.REGENERATE) {
            return regenerate(normalized, requestContext);
        }
        if (normalized.action() == ChatAction.COMMAND) {
            return commandRouter.route(normalized).map(control -> renderResponse(toControlResponse(normalized, control, "control"), requestContext));
        }
        return commandRouter.route(normalized)
                .map(control -> renderResponse(toControlResponse(normalized, control, "control"), requestContext))
                .switchIfEmpty(createSession(normalized, requestContext)
                        .flatMap(session -> runCompletion(session, requestContext)));
    }

    public Flux<ServerSentEvent<ChatChunk>> stream(ChatRequest request, RequestContext requestContext) {
        ChatRequest normalized = normalize(request, requestContext);
        if (normalized.action() == ChatAction.STOP || normalized.action() == ChatAction.COMMAND) {
            return commandRouter.route(normalized)
                    .flatMapMany(control -> Flux.just(controlEvent(normalized, control, "control", requestContext)));
        }
        if (normalized.action() == ChatAction.REGENERATE) {
            return regenerateStream(normalized, requestContext);
        }
        return commandRouter.route(normalized)
                .flatMapMany(control -> Flux.just(controlEvent(normalized, control, "control", requestContext)))
                .switchIfEmpty(createSession(normalized, requestContext)
                        .flatMapMany(session -> streamSession(session, requestContext)));
    }

    private Mono<ChatResponse> regenerate(ChatRequest request, RequestContext requestContext) {
        ConversationTurn turn = resolveTurnForRegeneration(request);
        if (turn == null) {
            return Mono.just(renderResponse(new ChatResponse(
                    UUID.randomUUID().toString(),
                    Instant.now(),
                    "littleclaw-control",
                    List.of(),
                    "No prior response found to regenerate.",
                    request.requestId(),
                    defaultConversationId(request.conversationId()),
                    request.parentMessageId(),
                    "not_found",
                    Map.of("action", "regenerate")
            ), requestContext));
        }
        return createSession(replayRequest(request, turn, false), requestContext)
                .flatMap(session -> runCompletion(session, requestContext));
    }

    private Flux<ServerSentEvent<ChatChunk>> regenerateStream(ChatRequest request, RequestContext requestContext) {
        ConversationTurn turn = resolveTurnForRegeneration(request);
        if (turn == null) {
            return Flux.just(controlEvent(request, new ChatControlResponse(
                    "ignored",
                    request.requestId(),
                    "No prior response found to regenerate.",
                    Map.of("action", "regenerate")
            ), "not_found", requestContext));
        }
        return createSession(replayRequest(request, turn, true), requestContext)
                .flatMapMany(session -> streamSession(session, requestContext));
    }

    private ConversationTurn resolveTurnForRegeneration(ChatRequest request) {
        if (StringUtils.hasText(request.regenerateFromResponseId())) {
            return conversationStore.findByResponseId(request.regenerateFromResponseId());
        }
        if (StringUtils.hasText(request.conversationId())) {
            return conversationStore.latestForConversation(request.conversationId());
        }
        return null;
    }

    private ChatRequest replayRequest(ChatRequest request, ConversationTurn turn, boolean stream) {
        return new ChatRequest(
                stream ? ChatAction.STREAM : ChatAction.COMPLETE,
                turn.requestMessages(),
                stream,
                request.maxTokens(),
                request.temperature(),
                request.channel(),
                request.conversationId(),
                request.requestId(),
                turn.responseId(),
                request.regenerateFromResponseId(),
                null
        );
    }

    private Mono<ChatResponse> runCompletion(ChatSession session, RequestContext requestContext) {
        return admissionController.acquire(new AdmissionRequest(session.tenantId(), false, session.plan().maxTokens()))
                .thenMany(chatEngine.stream(session.plan()).limitRate(properties.getStream().getDownstreamPrefetch()))
                .takeUntil(delta -> activeRequestRegistry.isCancelled(session.requestId()))
                .collectList()
                .map(parts -> String.join("", parts))
                .map(content -> completeSession(session, content, requestContext));
    }

    private Flux<ServerSentEvent<ChatChunk>> streamSession(ChatSession session, RequestContext requestContext) {
        return Flux.usingWhen(
                admissionController.acquire(new AdmissionRequest(session.tenantId(), true, session.plan().maxTokens())),
                lease -> streamWithLease(session, requestContext),
                lease -> lease.release().onErrorResume(error -> Mono.empty())
        );
    }

    private Flux<ServerSentEvent<ChatChunk>> streamWithLease(ChatSession session, RequestContext requestContext) {
        StringBuilder content = new StringBuilder();
        Flux<String> tokens = chatEngine.stream(session.plan())
                .limitRate(properties.getStream().getDownstreamPrefetch())
                .takeUntil(delta -> activeRequestRegistry.isCancelled(session.requestId()))
                .doOnNext(content::append);
        if (properties.getStream().getTokenDelayMs() > 0) {
            tokens = tokens.delayElements(Duration.ofMillis(properties.getStream().getTokenDelayMs()));
        }
        return tokens.map(delta -> renderChunk(new ChatChunk(
                                session.id(),
                                session.createdAt(),
                                session.providerId(),
                                session.skillNames(),
                                delta,
                                false,
                                session.requestId(),
                                session.conversationId(),
                                session.parentMessageId(),
                                "streaming",
                                session.metadata()
                        ), requestContext))
                .map(chunk -> ServerSentEvent.<ChatChunk>builder().event("chunk").id(session.id()).data(chunk).build())
                .concatWithValues(ServerSentEvent.<ChatChunk>builder()
                        .event(activeRequestRegistry.isCancelled(session.requestId()) ? "interrupted" : "done")
                        .id(session.id())
                        .data(renderChunk(new ChatChunk(
                                session.id(),
                                session.createdAt(),
                                session.providerId(),
                                session.skillNames(),
                                "",
                                true,
                                session.requestId(),
                                session.conversationId(),
                                session.parentMessageId(),
                                activeRequestRegistry.isCancelled(session.requestId()) ? "interrupted" : "completed",
                                session.metadata()
                        ), requestContext))
                        .build())
                .doOnComplete(() -> rememberConversation(
                        session,
                        content.toString(),
                        activeRequestRegistry.isCancelled(session.requestId()) ? "interrupted" : "completed"
                ));
    }

    private Mono<ChatSession> createSession(ChatRequest request, RequestContext requestContext) {
        List<ChatMessage> sessionMessages = sessionMessages(request);
        validate(sessionMessages, request.maxTokens(), request.temperature());
        String latestUserMessage = sessionMessages.get(sessionMessages.size() - 1).content();
        List<Skill> matchedSkills = skillRegistry.match(latestUserMessage, 4).stream().map(SkillMatch::skill).toList();
        return contextAssembler.assemble(
                        properties.getSystemPrompt(),
                        latestUserMessage,
                        matchedSkills,
                        request.channel(),
                        properties.getRag().getTopK()
                )
                .map(context -> buildSession(request, requestContext, sessionMessages, latestUserMessage, context));
    }

    private ChatSession buildSession(ChatRequest request,
                                     RequestContext requestContext,
                                     List<ChatMessage> sessionMessages,
                                     String latestUserMessage,
                                     ContextEnvelope context) {
        String requestId = request.requestId();
        activeRequestRegistry.register(requestId);
        ChatPlan plan = new ChatPlan(
                context.assembledSystemPrompt(),
                latestUserMessage,
                sessionMessages,
                context.matchedSkills(),
                context.ragSnippets(),
                context.mcpTools(),
                request.channel(),
                request.maxTokens(),
                request.temperature()
        );
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("ragHitCount", context.ragSnippets().size());
        metadata.put("mcpToolCount", context.mcpTools().size());
        metadata.put("hasChannel", request.channel() != null);
        metadata.put("protocolVersion", "2026-03-14.v4");
        metadata.put("action", request.action().name().toLowerCase());
        metadata.put("requestChannel", requestContext.channel());
        metadata.put("messageCount", sessionMessages.size());
        metadata.put("continuedConversation", sessionMessages.size() > request.messages().size());
        return new ChatSession(
                UUID.randomUUID().toString(),
                Instant.now(),
                requestId,
                defaultConversationId(request.conversationId()),
                request.parentMessageId(),
                requestContext.tenantId(),
                plan,
                context.matchedSkills().stream().map(Skill::name).toList(),
                "littleclaw-" + properties.getProvider().getType(),
                metadata
        );
    }

    private ChatResponse completeSession(ChatSession session, String content, RequestContext requestContext) {
        String finishReason = activeRequestRegistry.isCancelled(session.requestId()) ? "interrupted" : "completed";
        rememberConversation(session, content, finishReason);
        return renderResponse(new ChatResponse(
                session.id(),
                session.createdAt(),
                session.providerId(),
                session.skillNames(),
                content,
                session.requestId(),
                session.conversationId(),
                session.parentMessageId(),
                finishReason,
                session.metadata()
        ), requestContext);
    }

    private void rememberConversation(ChatSession session, String content, String finishReason) {
        try {
            if ("completed".equals(finishReason)) {
                activeRequestRegistry.rememberResponse(session.conversationId(), session.id(), session.requestId());
                conversationStore.save(session.conversationId(), session.id(), session.requestId(), session.plan().messages(), content);
            }
        } finally {
            activeRequestRegistry.complete(session.requestId());
        }
    }

    private ChatResponse toControlResponse(ChatRequest request, ChatControlResponse control, String finishReason) {
        return new ChatResponse(
                UUID.randomUUID().toString(),
                Instant.now(),
                "littleclaw-control",
                List.of(),
                control.message(),
                request.requestId(),
                defaultConversationId(request.conversationId()),
                request.parentMessageId(),
                finishReason,
                control.metadata()
        );
    }

    private ServerSentEvent<ChatChunk> controlEvent(ChatRequest request, ChatControlResponse control, String finishReason, RequestContext requestContext) {
        return ServerSentEvent.<ChatChunk>builder()
                .event("control")
                .id(request.requestId())
                .data(renderChunk(new ChatChunk(
                        UUID.randomUUID().toString(),
                        Instant.now(),
                        "littleclaw-control",
                        List.of(),
                        control.message(),
                        true,
                        request.requestId(),
                        defaultConversationId(request.conversationId()),
                        request.parentMessageId(),
                        finishReason,
                        control.metadata()
                ), requestContext))
                .build();
    }

    private ChatResponse renderResponse(ChatResponse response, RequestContext requestContext) {
        return channelResponseRenderer.render(response, requestContext.channel());
    }

    private ChatChunk renderChunk(ChatChunk chunk, RequestContext requestContext) {
        return channelResponseRenderer.render(chunk, requestContext.channel());
    }

    private void validate(List<ChatMessage> messages, Integer maxTokens, Double temperature) {
        if (messages.size() > properties.getLimits().getMaxMessages()) {
            throw new ValidationException("Too many messages in request.");
        }
        if (maxTokens != null && maxTokens > properties.getLimits().getMaxMaxTokens()) {
            throw new ValidationException("Requested maxTokens exceeds configured limit.");
        }
        if (temperature != null && (temperature < 0.0 || temperature > 2.0)) {
            throw new ValidationException("Temperature must be between 0.0 and 2.0.");
        }
        int totalChars = 0;
        for (ChatMessage message : messages) {
            if (!ALLOWED_ROLES.contains(message.role())) {
                throw new ValidationException("Unsupported message role: " + message.role());
            }
            if (!StringUtils.hasText(message.content())) {
                throw new ValidationException("Message content must not be blank.");
            }
            if (message.content().length() > properties.getLimits().getMaxMessageChars()) {
                throw new ValidationException("Message exceeds configured per-message size limit.");
            }
            totalChars += message.content().length();
        }
        if (totalChars > properties.getLimits().getMaxInputChars()) {
            throw new ValidationException("Input exceeds configured size limit.");
        }
    }

    private ChatRequest normalize(ChatRequest request, RequestContext requestContext) {
        Integer maxTokens = request.maxTokens() == null ? properties.getLimits().getDefaultMaxTokens() : request.maxTokens();
        Double temperature = request.temperature() == null ? 0.2 : request.temperature();
        String requestId = StringUtils.hasText(request.requestId()) ? request.requestId() : requestContext.requestId();
        ChatAction action = request.action() == null
                ? (request.stream() ? ChatAction.STREAM : ChatAction.COMPLETE)
                : request.action();
        return new ChatRequest(
                action,
                request.messages(),
                request.stream(),
                maxTokens,
                temperature,
                request.channel(),
                request.conversationId(),
                requestId,
                request.parentMessageId(),
                request.regenerateFromResponseId(),
                request.interruptRequestId()
        );
    }

    private List<ChatMessage> sessionMessages(ChatRequest request) {
        if (!StringUtils.hasText(request.conversationId()) || request.parentMessageId() != null || request.messages().size() != 1) {
            return request.messages();
        }
        ConversationTurn latestTurn = conversationStore.latestForConversation(request.conversationId());
        if (latestTurn == null || latestTurn.transcriptMessages() == null || latestTurn.transcriptMessages().isEmpty()) {
            return request.messages();
        }
        List<ChatMessage> merged = new java.util.ArrayList<>(latestTurn.transcriptMessages());
        merged.addAll(request.messages());
        return List.copyOf(merged);
    }

    private String defaultConversationId(String conversationId) {
        return StringUtils.hasText(conversationId) ? conversationId : "conv-" + UUID.randomUUID();
    }

    private record ChatSession(String id,
                               Instant createdAt,
                               String requestId,
                               String conversationId,
                               String parentMessageId,
                               String tenantId,
                               ChatPlan plan,
                               List<String> skillNames,
                               String providerId,
                               Map<String, Object> metadata) {
    }
}
