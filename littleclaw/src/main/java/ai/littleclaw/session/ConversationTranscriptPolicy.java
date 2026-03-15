package ai.littleclaw.session;

import ai.littleclaw.chat.ChatMessage;
import ai.littleclaw.config.LittleClawProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ConversationTranscriptPolicy {

    private final LittleClawProperties properties;

    public ConversationTranscriptPolicy(LittleClawProperties properties) {
        this.properties = properties;
    }

    public ConversationTurn buildTurn(String responseId, String requestId, List<ChatMessage> requestMessages, String responseContent) {
        List<ChatMessage> requestCopy = List.copyOf(requestMessages);
        List<ChatMessage> transcript = new ArrayList<>(requestMessages);
        if (responseContent != null && !responseContent.isBlank()) {
            transcript.add(new ChatMessage("assistant", responseContent));
        }
        transcript = trimTranscript(transcript);
        return new ConversationTurn(responseId, requestId, requestCopy, List.copyOf(transcript));
    }

    private List<ChatMessage> trimTranscript(List<ChatMessage> transcript) {
        int maxMessages = properties.getSession().getMaxTranscriptMessages();
        int maxChars = properties.getSession().getMaxTranscriptChars();
        int start = Math.max(0, transcript.size() - maxMessages);
        List<ChatMessage> trimmed = new ArrayList<>(transcript.subList(start, transcript.size()));
        while (totalChars(trimmed) > maxChars && trimmed.size() > 1) {
            trimmed.remove(0);
        }
        return trimmed;
    }

    private int totalChars(List<ChatMessage> messages) {
        int total = 0;
        for (ChatMessage message : messages) {
            total += message.content() == null ? 0 : message.content().length();
        }
        return total;
    }
}
