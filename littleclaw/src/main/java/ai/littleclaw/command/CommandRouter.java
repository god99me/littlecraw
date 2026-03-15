package ai.littleclaw.command;

import ai.littleclaw.chat.ChatControlResponse;
import ai.littleclaw.chat.ChatRequest;
import ai.littleclaw.session.ActiveRequestRegistry;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

@Component
public class CommandRouter {

    private final CommandInterpreter commandInterpreter;
    private final ActiveRequestRegistry activeRequestRegistry;

    public CommandRouter(CommandInterpreter commandInterpreter, ActiveRequestRegistry activeRequestRegistry) {
        this.commandInterpreter = commandInterpreter;
        this.activeRequestRegistry = activeRequestRegistry;
    }

    public Mono<ChatControlResponse> route(ChatRequest request) {
        String latest = request.messages().get(request.messages().size() - 1).content();
        CommandMatch match = commandInterpreter.match(latest);
        if (!match.matched()) {
            return Mono.empty();
        }
        return switch (match.action()) {
            case HELP -> Mono.just(new ChatControlResponse(
                    "handled",
                    request.requestId(),
                    "Built-in commands: /help, /stop, /regen, /ping",
                    Map.of("action", "help")
            ));
            case PING -> Mono.just(new ChatControlResponse(
                    "handled",
                    request.requestId(),
                    "pong",
                    Map.of("action", "ping")
            ));
            case STOP -> handleStop(request);
            case REGENERATE -> handleRegenerate(request);
            case NONE -> Mono.empty();
        };
    }

    private Mono<ChatControlResponse> handleStop(ChatRequest request) {
        String targetRequestId = request.interruptRequestId();
        if (targetRequestId == null || targetRequestId.isBlank()) {
            String parentMessageId = request.parentMessageId();
            if (parentMessageId != null && !parentMessageId.isBlank()) {
                String mappedRequestId = activeRequestRegistry.requestForResponse(parentMessageId);
                targetRequestId = mappedRequestId != null && !mappedRequestId.isBlank() ? mappedRequestId : parentMessageId;
            }
        }
        if (targetRequestId == null || targetRequestId.isBlank()) {
            return Mono.just(new ChatControlResponse(
                    "ignored",
                    request.requestId(),
                    "No target request id was provided for stop.",
                    Map.of("action", "stop")
            ));
        }
        activeRequestRegistry.cancel(targetRequestId);
        return Mono.just(new ChatControlResponse(
                "handled",
                request.requestId(),
                "Stop signal accepted for request " + targetRequestId,
                Map.of("action", "stop", "targetRequestId", targetRequestId)
        ));
    }

    private Mono<ChatControlResponse> handleRegenerate(ChatRequest request) {
        String targetResponseId = request.regenerateFromResponseId();
        if ((targetResponseId == null || targetResponseId.isBlank()) && request.conversationId() != null) {
            targetResponseId = activeRequestRegistry.latestResponse(request.conversationId());
        }
        return Mono.just(new ChatControlResponse(
                "handled",
                request.requestId(),
                "Regenerate requested.",
                Map.of("action", "regenerate", "targetResponseId", targetResponseId == null ? "" : targetResponseId)
        ));
    }
}
