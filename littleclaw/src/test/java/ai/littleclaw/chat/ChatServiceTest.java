package ai.littleclaw.chat;

import ai.littleclaw.config.LittleClawProperties;
import ai.littleclaw.skill.SkillLoader;
import ai.littleclaw.skill.SkillRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatServiceTest {

    private ChatService chatService;

    @BeforeEach
    void setUp() {
        LittleClawProperties properties = new LittleClawProperties();
        properties.setSystemPrompt("test");
        properties.getSkills().setPath("src/main/resources/skills");
        properties.getStream().setTokenDelayMs(0);
        SkillRegistry registry = new SkillRegistry(new SkillLoader(), properties);
        registry.refresh();
        chatService = new ChatService(registry, new HeuristicChatEngine(new ai.littleclaw.provider.StubChatProvider()), properties);
    }

    @Test
    void completesWithMatchedSkillNames() {
        ChatRequest request = new ChatRequest(List.of(new ChatMessage("user", "Can you help with Java architecture?")), false, null, null);
        ChatResponse response = chatService.complete(request);
        assertTrue(response.activeSkills().contains("code-helper"));
        assertTrue(response.content().contains("LittleClaw response"));
    }

    @Test
    void streamsDoneEvent() {
        ChatRequest request = new ChatRequest(List.of(new ChatMessage("user", "What is the weather tomorrow?")), true, null, null);
        StepVerifier.create(chatService.stream(request))
                .expectNextCount(1)
                .thenConsumeWhile(event -> event.data() != null && !event.data().done())
                .expectNextMatches(event -> event.data() != null && event.data().done())
                .verifyComplete();
    }
}
