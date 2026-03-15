package ai.littleclaw.channel;

import java.util.Set;

public record ChannelDescriptor(
        String name,
        Set<ChannelCapability> capabilities
) {
}
