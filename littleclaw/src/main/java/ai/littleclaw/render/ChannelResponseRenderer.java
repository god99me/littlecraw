package ai.littleclaw.render;

import ai.littleclaw.chat.ChatChunk;
import ai.littleclaw.chat.ChatResponse;

public interface ChannelResponseRenderer {

    ChatResponse render(ChatResponse response, String requestChannel);

    ChatChunk render(ChatChunk chunk, String requestChannel);
}
