package ai.littleclaw.session;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class ActiveRequestRegistry {

    private static final int MAX_TRACKED_RESPONSES = 4096;
    private static final int MAX_TRACKED_CONVERSATIONS = 4096;

    private final Map<String, Boolean> cancelled = java.util.Collections.synchronizedMap(new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
            return size() > MAX_TRACKED_RESPONSES;
        }
    });
    private final Map<String, String> latestResponseByConversation = java.util.Collections.synchronizedMap(new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > MAX_TRACKED_CONVERSATIONS;
        }
    });
    private final Map<String, String> parentRequestByResponse = java.util.Collections.synchronizedMap(new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > MAX_TRACKED_RESPONSES;
        }
    });

    public void register(String requestId) {
        if (requestId != null && !requestId.isBlank()) {
            cancelled.remove(requestId);
        }
    }

    public void cancel(String requestId) {
        if (requestId != null && !requestId.isBlank()) {
            cancelled.put(requestId, Boolean.TRUE);
        }
    }

    public boolean isCancelled(String requestId) {
        return requestId != null && cancelled.containsKey(requestId);
    }

    public void complete(String requestId) {
        if (requestId != null && !requestId.isBlank()) {
            cancelled.remove(requestId);
        }
    }

    public void rememberResponse(String conversationId, String responseId, String requestId) {
        if (conversationId != null && !conversationId.isBlank()) {
            latestResponseByConversation.put(conversationId, responseId);
        }
        if (responseId != null && !responseId.isBlank() && requestId != null && !requestId.isBlank()) {
            parentRequestByResponse.put(responseId, requestId);
        }
    }

    public String latestResponse(String conversationId) {
        return latestResponseByConversation.get(conversationId);
    }

    public String requestForResponse(String responseId) {
        return parentRequestByResponse.get(responseId);
    }
}
