package ai.littleclaw.api;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
public class RequestContextFilter implements WebFilter {

    public static final String ATTRIBUTE = RequestContext.class.getName();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String tenantId = headerOrDefault(request, "X-Tenant-Id", "anonymous");
        String requestId = headerOrDefault(request, "X-Request-Id", UUID.randomUUID().toString());
        String channel = headerOrDefault(request, "X-Channel", "api");
        exchange.getAttributes().put(ATTRIBUTE, new RequestContext(tenantId, requestId, channel));
        return chain.filter(exchange.mutate().request(builder -> builder.headers(headers -> headers.set("X-Request-Id", requestId))).build());
    }

    private String headerOrDefault(ServerHttpRequest request, String name, String fallback) {
        String value = request.getHeaders().getFirst(name);
        return StringUtils.hasText(value) ? value : fallback;
    }
}
