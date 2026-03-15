package ai.littleclaw.config;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class TenantPolicyResolver {

    private final LittleClawProperties properties;

    public TenantPolicyResolver(LittleClawProperties properties) {
        this.properties = properties;
    }

    public TenantPolicy resolve(String tenantId) {
        LittleClawProperties.Auth.Client client = findClient(tenantId);
        LittleClawProperties.Admission admission = properties.getAdmission();
        LittleClawProperties.Limits limits = properties.getLimits();
        return new TenantPolicy(
                normalizedTenantId(tenantId),
                client != null && StringUtils.hasText(client.getName()) ? client.getName() : normalizedTenantId(tenantId),
                client != null && client.getMaxRequestsPerWindow() != null ? client.getMaxRequestsPerWindow() : admission.getMaxRequestsPerWindow(),
                client != null && client.getMaxActiveStreams() != null ? client.getMaxActiveStreams() : admission.getMaxActiveStreamsPerTenant(),
                client != null && client.getMaxInputChars() != null ? client.getMaxInputChars() : limits.getMaxInputChars(),
                client != null && client.getMaxMaxTokens() != null ? client.getMaxMaxTokens() : limits.getMaxMaxTokens()
        );
    }

    private LittleClawProperties.Auth.Client findClient(String tenantId) {
        String normalized = normalizedTenantId(tenantId);
        for (LittleClawProperties.Auth.Client client : properties.getAuth().getClients()) {
            if (normalized.equals(client.getTenantId())) {
                return client;
            }
        }
        return null;
    }

    private String normalizedTenantId(String tenantId) {
        return StringUtils.hasText(tenantId) ? tenantId : "anonymous";
    }

    public record TenantPolicy(
            String tenantId,
            String tenantName,
            int maxRequestsPerWindow,
            int maxActiveStreamsPerTenant,
            int maxInputChars,
            int maxMaxTokens
    ) {
    }
}
