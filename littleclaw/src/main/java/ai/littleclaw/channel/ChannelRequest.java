package ai.littleclaw.channel;

public record ChannelRequest(
        String channel,
        String provider,
        String chatType,
        String userId,
        String conversationId,
        String messageId
) {
}
