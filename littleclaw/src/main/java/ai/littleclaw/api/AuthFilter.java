package ai.littleclaw.api;

import ai.littleclaw.config.LittleClawProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
public class AuthFilter implements WebFilter {

    private final LittleClawProperties properties;

    public AuthFilter(LittleClawProperties properties) {
        this.properties = properties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!properties.getAuth().isEnabled()) {
            return chain.filter(exchange);
        }
        String apiKey = exchange.getRequest().getHeaders().getFirst(properties.getAuth().getApiKeyHeader());
        if (StringUtils.hasText(apiKey) && properties.getAuth().getStaticApiKeys().contains(apiKey)) {
            return chain.filter(exchange);
        }
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }
}
