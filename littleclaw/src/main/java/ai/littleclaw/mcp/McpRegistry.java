package ai.littleclaw.mcp;

import ai.littleclaw.config.LittleClawProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class McpRegistry {

    private final LittleClawProperties properties;

    public McpRegistry(LittleClawProperties properties) {
        this.properties = properties;
    }

    public List<McpToolDescriptor> availableTools() {
        if (!properties.getMcp().isEnabled()) {
            return List.of();
        }
        return properties.getMcp().getServers().stream()
                .flatMap(server -> server.getTools().stream().map(tool -> new McpToolDescriptor(server.getName(), server.getTransport(), tool)))
                .toList();
    }
}
