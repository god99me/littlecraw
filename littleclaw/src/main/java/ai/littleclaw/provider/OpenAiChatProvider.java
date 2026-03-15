package ai.littleclaw.provider;

import ai.littleclaw.chat.ChatMessage;
import ai.littleclaw.config.LittleClawProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelOption;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "littleclaw.provider", name = "type", havingValue = "openai")
public class OpenAiChatProvider implements ChatProvider {

    private final LittleClawProperties properties;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    public OpenAiChatProvider(LittleClawProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, properties.getProvider().getOpenai().getConnectTimeoutMs())
                .responseTimeout(Duration.ofMillis(properties.getProvider().getOpenai().getResponseTimeoutMs()));
        this.webClient = WebClient.builder()
                .baseUrl(properties.getProvider().getOpenai().getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public String id() {
        return "openai-compatible";
    }

    @Override
    public Flux<ProviderChunk> stream(ProviderRequest request) {
        if (!StringUtils.hasText(properties.getProvider().getOpenai().getApiKey())) {
            return Flux.error(new IllegalStateException("OpenAI provider selected but no API key is configured."));
        }
        return webClient.post()
                .uri(properties.getProvider().getOpenai().getChatCompletionsPath())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getProvider().getOpenai().getApiKey())
                .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                .bodyValue(buildPayload(request))
                .retrieve()
                .bodyToFlux(String.class)
                .flatMap(this::decodeEventBlock)
                .limitRate(properties.getStream().getDownstreamPrefetch())
                .concatWithValues(new ProviderChunk("", true));
    }

    private Flux<ProviderChunk> decodeEventBlock(String block) {
        List<ProviderChunk> chunks = new ArrayList<>();
        for (String line : block.split("\\R")) {
            if (!line.startsWith("data:")) {
                continue;
            }
            String payload = line.substring(5).trim();
            if (payload.isEmpty() || "[DONE]".equals(payload)) {
                continue;
            }
            String delta = extractDelta(payload);
            if (StringUtils.hasText(delta)) {
                chunks.add(new ProviderChunk(delta, false));
            }
        }
        return Flux.fromIterable(chunks);
    }

    private String extractDelta(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                return "";
            }
            JsonNode choice = choices.get(0);
            JsonNode deltaNode = choice.path("delta").path("content");
            if (!deltaNode.isMissingNode() && !deltaNode.isNull()) {
                return deltaNode.asText("");
            }
            JsonNode messageNode = choice.path("message").path("content");
            if (!messageNode.isMissingNode() && !messageNode.isNull()) {
                return messageNode.asText("");
            }
            return "";
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to decode upstream SSE payload.", exception);
        }
    }

    private Map<String, Object> buildPayload(ProviderRequest request) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", request.systemPrompt()));
        for (ChatMessage message : request.messages()) {
            messages.add(Map.of("role", message.role(), "content", message.content()));
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", properties.getProvider().getOpenai().getModel());
        payload.put("stream", true);
        payload.put("messages", messages);
        payload.put("max_tokens", request.maxTokens());
        payload.put("temperature", request.temperature());
        return payload;
    }
}
