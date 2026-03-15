package ai.littleclaw.api;

import ai.littleclaw.admission.InMemoryAdmissionController;
import ai.littleclaw.channel.ChannelRegistry;
import ai.littleclaw.chat.ChatAction;
import ai.littleclaw.chat.ChatService;
import ai.littleclaw.chat.HeuristicChatEngine;
import ai.littleclaw.command.CommandInterpreter;
import ai.littleclaw.command.CommandRouter;
import ai.littleclaw.config.LittleClawProperties;
import ai.littleclaw.context.ContextAssembler;
import ai.littleclaw.mcp.McpRegistry;
import ai.littleclaw.observability.ChatMetrics;
import ai.littleclaw.config.TenantPolicyResolver;
import ai.littleclaw.provider.StubChatProvider;
import ai.littleclaw.rag.LocalFilesystemRagService;
import ai.littleclaw.render.DefaultChannelResponseRenderer;
import ai.littleclaw.render.RenderPolicyRegistry;
import ai.littleclaw.session.ActiveRequestRegistry;
import ai.littleclaw.session.ConversationTranscriptPolicy;
import ai.littleclaw.session.InMemoryConversationStore;
import ai.littleclaw.skill.SkillLoader;
import ai.littleclaw.skill.SkillRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.List;
import java.util.Map;

class ChatControllerTest {

    private WebTestClient client;
    private ActiveRequestRegistry activeRequestRegistry;
    private LittleClawProperties properties;

    @BeforeEach
    void setUp() {
        properties = new LittleClawProperties();
        properties.setSystemPrompt("test");
        properties.getSkills().setPath("src/main/resources/skills");
        properties.getStream().setTokenDelayMs(0);
        properties.getAdmission().setMaxRequestsPerWindow(1);
        properties.getRag().getIncludePaths().clear();
        properties.getRag().getIncludePaths().add("src/main/resources/skills");
        SkillRegistry registry = new SkillRegistry(new SkillLoader(), properties);
        registry.refresh();
        ChatMetrics metrics = new ChatMetrics(new SimpleMeterRegistry());
        TenantPolicyResolver tenantPolicyResolver = new TenantPolicyResolver(properties);
        activeRequestRegistry = new ActiveRequestRegistry();
        ChatService chatService = new ChatService(
                registry,
                new HeuristicChatEngine(new StubChatProvider(properties)),
                properties,
                new InMemoryAdmissionController(properties, tenantPolicyResolver, metrics),
                new ContextAssembler(new LocalFilesystemRagService(properties), new McpRegistry(properties), new ChannelRegistry()),
                new CommandRouter(new CommandInterpreter(), activeRequestRegistry),
                activeRequestRegistry,
                new InMemoryConversationStore(new ConversationTranscriptPolicy(properties), properties, metrics),
                new DefaultChannelResponseRenderer(new RenderPolicyRegistry()),
                tenantPolicyResolver,
                metrics
        );
        client = WebTestClient.bindToController(new ChatController(chatService, registry, activeRequestRegistry))
                .webFilter(new AuthFilter(properties, metrics))
                .webFilter(new RequestContextFilter())
                .controllerAdvice(new GlobalExceptionHandler(metrics))
                .build();
    }

    @Test
    void completesChatRequest() {
        Map<String, Object> payload = Map.of(
                "action", ChatAction.COMPLETE.name(),
                "stream", false,
                "requestId", "req-api-1",
                "conversationId", "conv-api-1",
                "messages", List.of(Map.of("role", "user", "content", "Need Java code help")),
                "channel", Map.of("channel", "feishu", "provider", "feishu", "chatType", "direct")
        );
        client.post()
                .uri("/v1/chat/completions")
                .header("X-Tenant-Id", "tenant-a")
                .header("X-Request-Id", "ctx-request-1")
                .header("X-Channel", "feishu")
                .bodyValue(payload)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.activeSkills[0]").exists()
                .jsonPath("$.requestId").isEqualTo("req-api-1")
                .jsonPath("$.conversationId").isEqualTo("conv-api-1")
                .jsonPath("$.finishReason").isEqualTo("completed")
                .jsonPath("$.status").isEqualTo("ok")
                .jsonPath("$.metadata.protocolVersion").isEqualTo("2026-03-15.v5")
                .jsonPath("$.metadata.renderedFor").isEqualTo("feishu");
    }

    @Test
    void supportsRegenerateFlow() {
        Map<String, Object> first = Map.of(
                "action", ChatAction.COMPLETE.name(),
                "conversationId", "conv-regen-api",
                "requestId", "req-first-api",
                "messages", List.of(Map.of("role", "user", "content", "Need Java code help"))
        );
        client.post()
                .uri("/v1/chat/completions")
                .bodyValue(first)
                .exchange()
                .expectStatus().isOk();

        Map<String, Object> regenerate = Map.of(
                "action", ChatAction.REGENERATE.name(),
                "conversationId", "conv-regen-api",
                "requestId", "req-regen-api",
                "messages", List.of(Map.of("role", "user", "content", "重新生成"))
        );
        client.post()
                .uri("/v1/chat/completions")
                .bodyValue(regenerate)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.finishReason").isEqualTo("completed")
                .jsonPath("$.status").isEqualTo("ok");
    }

    @Test
    void continuesConversationAcrossRequests() {
        Map<String, Object> first = Map.of(
                "action", ChatAction.COMPLETE.name(),
                "conversationId", "conv-follow-api",
                "requestId", "req-follow-first",
                "messages", List.of(Map.of("role", "user", "content", "Need Java code help"))
        );
        client.post()
                .uri("/v1/chat/completions")
                .bodyValue(first)
                .exchange()
                .expectStatus().isOk();

        Map<String, Object> followUp = Map.of(
                "action", ChatAction.COMPLETE.name(),
                "conversationId", "conv-follow-api",
                "requestId", "req-follow-second",
                "messages", List.of(Map.of("role", "user", "content", "Continue with SSE tuning"))
        );
        client.post()
                .uri("/v1/chat/completions")
                .bodyValue(followUp)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.metadata.continuedConversation").isEqualTo(true)
                .jsonPath("$.metadata.messageCount").isEqualTo(3);
    }

    @Test
    void interceptsBuiltinCommand() {
        Map<String, Object> payload = Map.of(
                "action", ChatAction.COMMAND.name(),
                "stream", false,
                "requestId", "req-help-1",
                "messages", List.of(Map.of("role", "user", "content", "/help"))
        );
        client.post()
                .uri("/v1/chat/completions")
                .bodyValue(payload)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content").value(value -> org.assertj.core.api.Assertions.assertThat(String.valueOf(value)).contains("Built-in commands"));
    }

    @Test
    void rejectsMissingApiKeyWhenAuthEnabled() {
        properties.getAuth().setEnabled(true);
        Map<String, Object> payload = Map.of(
                "action", ChatAction.COMPLETE.name(),
                "messages", List.of(Map.of("role", "user", "content", "Need Java code help"))
        );
        client.post()
                .uri("/v1/chat/completions")
                .bodyValue(payload)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void bindsTenantFromConfiguredApiKey() {
        properties.getAuth().setEnabled(true);
        LittleClawProperties.Auth.Client clientConfig = new LittleClawProperties.Auth.Client();
        clientConfig.setTenantId("tenant-gold");
        clientConfig.setApiKey("secret-gold-key");
        clientConfig.setName("gold");
        properties.getAuth().getClients().add(clientConfig);
        Map<String, Object> payload = Map.of(
                "action", ChatAction.COMPLETE.name(),
                "messages", List.of(Map.of("role", "user", "content", "Need Java code help"))
        );
        client.post()
                .uri("/v1/chat/completions")
                .header("X-API-Key", "secret-gold-key")
                .bodyValue(payload)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.requestId").exists();
    }
}
