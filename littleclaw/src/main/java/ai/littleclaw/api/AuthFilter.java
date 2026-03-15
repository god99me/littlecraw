package ai.littleclaw.api;

import ai.littleclaw.config.LittleClawProperties;
import ai.littleclaw.observability.ChatMetrics;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuthFilter implements WebFilter {

    private final LittleClawProperties properties;
    private final ChatMetrics metrics;

    public AuthFilter(LittleClawProperties properties, ChatMetrics metrics) {
        this.properties = properties;
        this.metrics = metrics;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!properties.getAuth().isEnabled()) {
            metrics.recordAuth("disabled", "anonymous");
            return chain.filter(exchange);
        }
        ServerHttpRequest request = exchange.getRequest();
        String apiKey = request.getHeaders().getFirst(properties.getAuth().getApiKeyHeader());
        if (!StringUtils.hasText(apiKey)) {
            metrics.recordAuth("missing_key", "anonymous");
            return unauthorized(exchange, "Missing API key.");
        }
        if (properties.getAuth().getStaticApiKeys().contains(apiKey)) {
            metrics.recordAuth("static_key", headerOrDefault(request, "X-Tenant-Id", "anonymous"));
            return chain.filter(exchange);
        }
        for (LittleClawProperties.Auth.Client client : properties.getAuth().getClients()) {
            if (!apiKey.equals(client.getApiKey())) {
                continue;
            }
            String requestedTenantId = request.getHeaders().getFirst("X-Tenant-Id");
            if (StringUtils.hasText(requestedTenantId) && !client.getTenantId().equals(requestedTenantId)) {
                metrics.recordAuth("tenant_mismatch", requestedTenantId);
                return unauthorized(exchange, "API key is not allowed to access tenant " + requestedTenantId + ".");
            }
            metrics.recordAuth("tenant_key", client.getTenantId());
            return chain.filter(exchange.mutate().request(builder -> builder.headers(headers -> headers.set("X-Tenant-Id", client.getTenantId()))).build());
        }
        metrics.recordAuth("invalid_key", headerOrDefault(request, "X-Tenant-Id", "anonymous"));
        return unauthorized(exchange, "Invalid API key.");
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().set("X-Auth-Error", message);
        return exchange.getResponse().setComplete();
    }

    private String headerOrDefault(ServerHttpRequest request, String name, String fallback) {
        String value = request.getHeaders().getFirst(name);
        return StringUtils.hasText(value) ? value : fallback;
    }
}
