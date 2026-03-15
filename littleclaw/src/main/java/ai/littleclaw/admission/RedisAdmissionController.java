package ai.littleclaw.admission;

import ai.littleclaw.config.LittleClawProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
@ConditionalOnBean(ReactiveStringRedisTemplate.class)
@ConditionalOnProperty(prefix = "littleclaw.admission", name = "redis-enabled", havingValue = "true")
public class RedisAdmissionController implements AdmissionController {

    private static final RedisScript<Long> ACQUIRE_SCRIPT = RedisScript.of("""
            local rateKey = KEYS[1]
            local globalKey = KEYS[2]
            local tenantKey = KEYS[3]
            local windowSeconds = tonumber(ARGV[1])
            local maxRequests = tonumber(ARGV[2])
            local streaming = tonumber(ARGV[3])
            local maxGlobal = tonumber(ARGV[4])
            local maxTenant = tonumber(ARGV[5])
            local rate = redis.call('INCR', rateKey)
            if rate == 1 then
              redis.call('EXPIRE', rateKey, windowSeconds)
            end
            if rate > maxRequests then
              return -1
            end
            if streaming == 0 then
              return 1
            end
            local global = redis.call('INCR', globalKey)
            local tenant = redis.call('INCR', tenantKey)
            if tenant == 1 then
              redis.call('EXPIRE', tenantKey, windowSeconds)
            end
            if global > maxGlobal then
              redis.call('DECR', globalKey)
              redis.call('DECR', tenantKey)
              return -2
            end
            if tenant > maxTenant then
              redis.call('DECR', tenantKey)
              redis.call('DECR', globalKey)
              return -3
            end
            return 2
            """, Long.class);

    private static final RedisScript<Long> RELEASE_SCRIPT = RedisScript.of("""
            local globalKey = KEYS[1]
            local tenantKey = KEYS[2]
            local global = tonumber(redis.call('GET', globalKey) or '0')
            if global > 0 then
              redis.call('DECR', globalKey)
            end
            local tenant = tonumber(redis.call('GET', tenantKey) or '0')
            if tenant > 1 then
              redis.call('DECR', tenantKey)
            else
              redis.call('DEL', tenantKey)
            end
            return 1
            """, Long.class);

    private final ReactiveStringRedisTemplate redis;
    private final LittleClawProperties properties;

    public RedisAdmissionController(ReactiveStringRedisTemplate redis, LittleClawProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    @Override
    public Mono<AdmissionLease> acquire(AdmissionRequest request) {
        String prefix = properties.getAdmission().getRedisPrefix();
        String tenantId = request.tenantId();
        String rateKey = prefix + ":rate:" + tenantId;
        String globalKey = prefix + ":stream:global";
        String tenantKey = prefix + ":stream:tenant:" + tenantId;
        return redis.execute(
                        ACQUIRE_SCRIPT,
                        List.of(rateKey, globalKey, tenantKey),
                        String.valueOf(properties.getAdmission().getRateWindowSeconds()),
                        String.valueOf(properties.getAdmission().getMaxRequestsPerWindow()),
                        request.streaming() ? "1" : "0",
                        String.valueOf(properties.getAdmission().getMaxActiveStreams()),
                        String.valueOf(properties.getAdmission().getMaxActiveStreamsPerTenant())
                )
                .next()
                .switchIfEmpty(Mono.error(new AdmissionRejectedException("Admission controller returned no result.")))
                .flatMap(code -> switch (code.intValue()) {
                    case -1 -> Mono.error(new AdmissionRejectedException("Tenant rate limit exceeded."));
                    case -2 -> Mono.error(new AdmissionRejectedException("Too many active streams."));
                    case -3 -> Mono.error(new AdmissionRejectedException("Tenant active stream quota exceeded."));
                    case 1 -> Mono.just(AdmissionLease.noOp());
                    case 2 -> Mono.just(buildStreamLease(globalKey, tenantKey));
                    default -> Mono.error(new AdmissionRejectedException("Unknown admission result: " + code));
                });
    }

    private AdmissionLease buildStreamLease(String globalKey, String tenantKey) {
        return () -> redis.execute(RELEASE_SCRIPT, List.of(globalKey, tenantKey))
                .next()
                .then();
    }
}
