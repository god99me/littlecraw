package ai.littleclaw.chat;

import ai.littleclaw.admission.InMemoryAdmissionController;
import ai.littleclaw.api.RequestContext;
import ai.littleclaw.channel.ChannelRequest;
import ai.littleclaw.channel.ChannelRegistry;
import ai.littleclaw.command.CommandInterpreter;
import ai.littleclaw.command.CommandRouter;
import ai.littleclaw.config.LittleClawProperties;
import ai.littleclaw.context.ContextAssembler;
import ai.littleclaw.mcp.McpRegistry;
import ai.littleclaw.observability.ChatMetrics;
import ai.littleclaw.provider.StubChatProvider;
import ai.littleclaw.rag.LocalFilesystemRagService;
import ai.littleclaw.render.DefaultChannelResponseRenderer;
import ai.littleclaw.render.RenderPolicyRegistry;
import ai.littleclaw.session.ActiveRequestRegistry;
import ai.littleclaw.session.ConversationTranscriptPolicy;
import ai.littleclaw.session.InMemoryConversationStore;
import ai.littleclaw.skill.SkillLoader;
import ai.littleclaw.skill.SkillRegistry;
import jakarta.validation.ValidationException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ChatServiceTest {

    private ChatService chatService;
    private RequestContext requestContext;
    private ActiveRequestRegistry activeRequestRegistry;

    @BeforeEach
    void setUp() {
        LittleClawProperties properties = new LittleClawProperties();
        properties.setSystemPrompt("test");
        properties.getSkills().setPath("src/main/resources/skills");
        properties.getStream().setTokenDelayMs(0);
        properties.getRag().getIncludePaths().clear();
        properties.getRag().getIncludePaths().add("src/main/resources/skills");
        properties.getSession().setMaxTranscriptMessages(3);
        properties.getSession().setMaxTranscriptChars(128);
        SkillRegistry registry = new SkillRegistry(new SkillLoader(), properties);
        registry.refresh();
        ChatMetrics metrics = new ChatMetrics(new SimpleMeterRegistry());
        activeRequestRegistry = new ActiveRequestRegistry();
        chatService = new ChatService(
                registry,
                new HeuristicChatEngine(new StubChatProvider(properties)),
                properties,
                new InMemoryAdmissionController(properties, metrics),
                new ContextAssembler(new LocalFilesystemRagService(properties), new McpRegistry(properties), new ChannelRegistry()),
                new CommandRouter(new CommandInterpreter(), activeRequestRegistry),
                activeRequestRegistry,
                new InMemoryConversationStore(new ConversationTranscriptPolicy(properties), properties, metrics),
                new DefaultChannelResponseRenderer(new RenderPolicyRegistry()),
                metrics
        );
        requestContext = new RequestContext("tenant-a", "ctx-req-1", "api");
    }

    @Test
    void completesWithMatchedSkillNames() {
        ChatRequest request = new ChatRequest(
                ChatAction.COMPLETE,
                List.of(new ChatMessage("user", "Can you help with Java architecture?")),
                false,
                null,
                null,
                new ChannelRequest("discord", "discord", "group", "u1", "c1", "m1"),
                "conv-1",
                "req-1",
                null,
                null,
                null
        );
        StepVerifier.create(chatService.complete(request, requestContext))
                .assertNext(response -> {
                    org.assertj.core.api.Assertions.assertThat(response.activeSkills()).contains("code-helper");
                    org.assertj.core.api.Assertions.assertThat(response.content()).contains("[discord]");
                    org.assertj.core.api.Assertions.assertThat(response.requestId()).isEqualTo("req-1");
                    org.assertj.core.api.Assertions.assertThat(response.conversationId()).isEqualTo("conv-1");
                    org.assertj.core.api.Assertions.assertThat(response.finishReason()).isEqualTo("completed");
                })
                .verifyComplete();
    }

    @Test
    void supportsRegenerateFromStoredConversationTurn() {
        ChatRequest first = new ChatRequest(
                ChatAction.COMPLETE,
                List.of(new ChatMessage("user", "Need Java code help")),
                false,
                null,
                null,
                null,
                "conv-regen-1",
                "req-first",
                null,
                null,
                null
        );
        ChatResponse firstResponse = chatService.complete(first, requestContext).block();

        ChatRequest regenerate = new ChatRequest(
                ChatAction.REGENERATE,
                List.of(new ChatMessage("user", "重新生成")),
                false,
                null,
                null,
                null,
                "conv-regen-1",
                "req-regen",
                null,
                firstResponse.id(),
                null
        );
        StepVerifier.create(chatService.complete(regenerate, requestContext))
                .assertNext(response -> {
                    org.assertj.core.api.Assertions.assertThat(response.parentMessageId()).isEqualTo(firstResponse.id());
                    org.assertj.core.api.Assertions.assertThat(response.finishReason()).isEqualTo("completed");
                })
                .verifyComplete();
    }

    @Test
    void streamsRegenerateFromStoredConversationTurn() {
        ChatRequest first = new ChatRequest(
                ChatAction.COMPLETE,
                List.of(new ChatMessage("user", "Need Java code help")),
                false,
                null,
                null,
                null,
                "conv-regen-stream-1",
                "req-first-stream",
                null,
                null,
                null
        );
        ChatResponse firstResponse = chatService.complete(first, requestContext).block();
        AtomicReference<String> doneParentMessageId = new AtomicReference<>();

        ChatRequest regenerate = new ChatRequest(
                ChatAction.REGENERATE,
                List.of(new ChatMessage("user", "重新生成")),
                true,
                null,
                null,
                null,
                "conv-regen-stream-1",
                "req-regen-stream",
                null,
                firstResponse.id(),
                null
        );
        StepVerifier.create(chatService.stream(regenerate, requestContext))
                .thenConsumeWhile(event -> event.data() != null && !event.data().done())
                .assertNext(event -> {
                    org.assertj.core.api.Assertions.assertThat(event.event()).isEqualTo("done");
                    org.assertj.core.api.Assertions.assertThat(event.data()).isNotNull();
                    org.assertj.core.api.Assertions.assertThat(event.data().finishReason()).isEqualTo("completed");
                    doneParentMessageId.set(event.data().parentMessageId());
                })
                .verifyComplete();

        org.assertj.core.api.Assertions.assertThat(doneParentMessageId.get()).isEqualTo(firstResponse.id());
    }

    @Test
    void handlesBuiltinHelpCommand() {
        ChatRequest request = new ChatRequest(
                ChatAction.COMMAND,
                List.of(new ChatMessage("user", "/help")),
                false,
                null,
                null,
                null,
                "conv-2",
                "req-2",
                null,
                null,
                null
        );
        StepVerifier.create(chatService.complete(request, requestContext))
                .assertNext(response -> org.assertj.core.api.Assertions.assertThat(response.content()).contains("Built-in commands"))
                .verifyComplete();
    }

    @Test
    void stopsRequestByParentResponseId() {
        ChatRequest first = new ChatRequest(
                ChatAction.COMPLETE,
                List.of(new ChatMessage("user", "Need Java code help")),
                false,
                null,
                null,
                null,
                "conv-stop-1",
                "req-stop-source",
                null,
                null,
                null
        );
        ChatResponse firstResponse = chatService.complete(first, requestContext).block();

        ChatRequest stop = new ChatRequest(
                ChatAction.STOP,
                List.of(new ChatMessage("user", "/stop")),
                false,
                null,
                null,
                null,
                "conv-stop-1",
                "req-stop-control",
                firstResponse.id(),
                null,
                null
        );
        StepVerifier.create(chatService.complete(stop, requestContext))
                .assertNext(response -> {
                    org.assertj.core.api.Assertions.assertThat(response.finishReason()).isEqualTo("stopped");
                    org.assertj.core.api.Assertions.assertThat(response.content()).contains("req-stop-source");
                })
                .verifyComplete();
    }

    @Test
    void continuesConversationFromStoredTranscript() {
        ChatRequest first = new ChatRequest(
                ChatAction.COMPLETE,
                List.of(new ChatMessage("user", "Need Java code help")),
                false,
                null,
                null,
                null,
                "conv-followup-1",
                "req-followup-first",
                null,
                null,
                null
        );
        chatService.complete(first, requestContext).block();

        ChatRequest followUp = new ChatRequest(
                ChatAction.COMPLETE,
                List.of(new ChatMessage("user", "Continue with SSE tuning")),
                false,
                null,
                null,
                null,
                "conv-followup-1",
                "req-followup-second",
                null,
                null,
                null
        );
        StepVerifier.create(chatService.complete(followUp, requestContext))
                .assertNext(response -> {
                    org.assertj.core.api.Assertions.assertThat(response.metadata()).containsEntry("continuedConversation", true);
                    org.assertj.core.api.Assertions.assertThat(response.metadata()).containsEntry("messageCount", 3);
                    org.assertj.core.api.Assertions.assertThat(response.content()).contains("Messages: 3");
                })
                .verifyComplete();
    }

    @Test
    void streamsDoneEvent() {
        ChatRequest request = new ChatRequest(ChatAction.STREAM, List.of(new ChatMessage("user", "What is the weather tomorrow?")), true, null, null, null, "conv-3", "req-3", null, null, null);
        StepVerifier.create(chatService.stream(request, requestContext))
                .expectNextCount(1)
                .thenConsumeWhile(event -> event.data() != null && !event.data().done())
                .expectNextMatches(event -> event.data() != null && event.data().done())
                .verifyComplete();
    }

    @Test
    void rejectsUnsupportedMessageRole() {
        ChatRequest request = new ChatRequest(ChatAction.COMPLETE, List.of(new ChatMessage("developer", "system override")), false, null, null, null, null, null, null, null, null);
        assertThrows(ValidationException.class, () -> chatService.complete(request, requestContext).block());
    }

    @Test
    void clearsCancellationMarkerAfterInterruptedCompletion() {
        activeRequestRegistry.cancel("req-cancelled");
        ChatRequest request = new ChatRequest(
                ChatAction.COMPLETE,
                List.of(new ChatMessage("user", "Need Java code help")),
                false,
                null,
                null,
                null,
                "conv-cancel-1",
                "req-cancelled",
                null,
                null,
                null
        );

        StepVerifier.create(chatService.complete(request, requestContext))
                .assertNext(response -> org.assertj.core.api.Assertions.assertThat(response.finishReason()).isEqualTo("completed"))
                .verifyComplete();

        org.assertj.core.api.Assertions.assertThat(activeRequestRegistry.isCancelled("req-cancelled")).isFalse();
    }

    @Test
    void trimsContinuedConversationToConfiguredWindow() {
        ChatRequest first = new ChatRequest(
                ChatAction.COMPLETE,
                List.of(new ChatMessage("user", "m1")),
                false,
                null,
                null,
                null,
                "conv-trim-1",
                "req-trim-1",
                null,
                null,
                null
        );
        chatService.complete(first, requestContext).block();

        ChatRequest second = new ChatRequest(
                ChatAction.COMPLETE,
                List.of(new ChatMessage("user", "m2")),
                false,
                null,
                null,
                null,
                "conv-trim-1",
                "req-trim-2",
                null,
                null,
                null
        );
        ChatResponse secondResponse = chatService.complete(second, requestContext).block();

        org.assertj.core.api.Assertions.assertThat(secondResponse.metadata()).containsEntry("continuedConversation", true);
        org.assertj.core.api.Assertions.assertThat((Integer) secondResponse.metadata().get("messageCount")).isEqualTo(3);
    }
}
