package ai.littleclaw.channel;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
public class ChannelRegistry {

    private final Map<String, ChannelDescriptor> descriptors = Map.of(
            "openai", new ChannelDescriptor("openai", Set.of(ChannelCapability.TEXT_IN, ChannelCapability.TEXT_OUT, ChannelCapability.STREAMING_OUT)),
            "feishu", new ChannelDescriptor("feishu", Set.of(ChannelCapability.TEXT_IN, ChannelCapability.TEXT_OUT, ChannelCapability.ATTACHMENTS, ChannelCapability.CARDS)),
            "telegram", new ChannelDescriptor("telegram", Set.of(ChannelCapability.TEXT_IN, ChannelCapability.TEXT_OUT, ChannelCapability.ATTACHMENTS, ChannelCapability.REACTIONS)),
            "discord", new ChannelDescriptor("discord", Set.of(ChannelCapability.TEXT_IN, ChannelCapability.TEXT_OUT, ChannelCapability.ATTACHMENTS, ChannelCapability.REACTIONS, ChannelCapability.THREADS)),
            "slack", new ChannelDescriptor("slack", Set.of(ChannelCapability.TEXT_IN, ChannelCapability.TEXT_OUT, ChannelCapability.ATTACHMENTS, ChannelCapability.THREADS)),
            "wechat", new ChannelDescriptor("wechat", Set.of(ChannelCapability.TEXT_IN, ChannelCapability.TEXT_OUT, ChannelCapability.ATTACHMENTS)),
            "whatsapp", new ChannelDescriptor("whatsapp", Set.of(ChannelCapability.TEXT_IN, ChannelCapability.TEXT_OUT, ChannelCapability.ATTACHMENTS)),
            "api", new ChannelDescriptor("api", Set.of(ChannelCapability.TEXT_IN, ChannelCapability.TEXT_OUT, ChannelCapability.STREAMING_OUT))
    );

    public Optional<ChannelDescriptor> find(String channel) {
        if (channel == null || channel.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(descriptors.get(channel.toLowerCase()));
    }

    public List<ChannelDescriptor> all() {
        return descriptors.values().stream().toList();
    }
}
