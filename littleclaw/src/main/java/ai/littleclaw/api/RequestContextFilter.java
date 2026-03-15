package ai.littleclaw.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestContextFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestContextFilter.class);

    public static final String ATTRIBUTE = RequestContext.class.getName();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String tenantId = headerOrDefault(request, "X-Tenant-Id", "anonymous");
        String requestId = headerOrDefault(request, "X-Request-Id", UUID.randomUUID().toString());
        String channel = headerOrDefault(request, "X-Channel", "api");
        exchange.getAttributes().put(ATTRIBUTE, new RequestContext(tenantId, requestId, channel));
        log.info("request.start method={} path={} tenantId={} requestId={} channel={}", request.getMethod(), request.getPath(), tenantId, requestId, channel);
        return chain.filter(exchange.mutate().request(builder -> builder.headers(headers -> headers.set("X-Request-Id", requestId))).build())
                .doOnSuccess(ignored -> log.info("request.finish method={} path={} tenantId={} requestId={} status={}", request.getMethod(), request.getPath(), tenantId, requestId, exchange.getResponse().getStatusCode()));
    }

    private String headerOrDefault(ServerHttpRequest request, String name, String fallback) {
        String value = request.getHeaders().getFirst(name);
        return StringUtils.hasText(value) ? value : fallback;
    }
}
