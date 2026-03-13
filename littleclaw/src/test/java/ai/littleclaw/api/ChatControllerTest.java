package ai.littleclaw.api;

import ai.littleclaw.config.AppConfig;
import ai.littleclaw.config.LittleClawProperties;
import ai.littleclaw.chat.ChatService;
import ai.littleclaw.chat.HeuristicChatEngine;
import ai.littleclaw.skill.SkillLoader;
import ai.littleclaw.skill.SkillRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.List;
import java.util.Map;

class ChatControllerTest {

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        LittleClawProperties properties = new LittleClawProperties();
        properties.setSystemPrompt("test");
        properties.getSkills().setPath("src/main/resources/skills");
        properties.getStream().setTokenDelayMs(0);
        SkillRegistry registry = new SkillRegistry(new SkillLoader(), properties);
        registry.refresh();
        ChatService chatService = new ChatService(registry, new HeuristicChatEngine(new ai.littleclaw.provider.StubChatProvider()), properties);
        client = WebTestClient.bindToController(new ChatController(chatService, registry))
                .controllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void returnsSkillList() {
        client.get()
                .uri("/v1/skills")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.count").isEqualTo(2);
    }

    @Test
    void completesChatRequest() {
        Map<String, Object> payload = Map.of(
                "stream", false,
                "messages", List.of(Map.of("role", "user", "content", "Need Java code help"))
        );
        client.post()
                .uri("/v1/chat/completions")
                .bodyValue(payload)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.activeSkills[0]").exists()
                .jsonPath("$.content").value(value -> org.assertj.core.api.Assertions.assertThat(String.valueOf(value)).contains("LittleClaw response"));
    }
}
