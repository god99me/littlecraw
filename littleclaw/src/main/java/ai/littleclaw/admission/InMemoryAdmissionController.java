package ai.littleclaw.admission;

import ai.littleclaw.config.LittleClawProperties;
import ai.littleclaw.config.TenantPolicyResolver;
import ai.littleclaw.observability.ChatMetrics;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class InMemoryAdmissionController implements AdmissionController {

    private final LittleClawProperties properties;
    private final TenantPolicyResolver tenantPolicyResolver;
    private final ChatMetrics metrics;
    private final AtomicInteger activeStreams = new AtomicInteger();
    private final Map<String, AtomicInteger> activeStreamsByTenant = new ConcurrentHashMap<>();
    private final Map<String, WindowCounter> requestCounters = new ConcurrentHashMap<>();

    public InMemoryAdmissionController(LittleClawProperties properties, TenantPolicyResolver tenantPolicyResolver, ChatMetrics metrics) {
        this.properties = properties;
        this.tenantPolicyResolver = tenantPolicyResolver;
        this.metrics = metrics;
    }

    @Override
    public Mono<AdmissionLease> acquire(AdmissionRequest request) {
        if (!properties.getAdmission().isEnabled()) {
            metrics.recordAdmission("disabled", request.streaming());
            return Mono.just(AdmissionLease.noOp());
        }
        TenantPolicyResolver.TenantPolicy tenantPolicy = tenantPolicyResolver.resolve(request.tenantId());
        WindowCounter counter = requestCounters.computeIfAbsent(request.tenantId(), ignored -> new WindowCounter());
        if (!counter.tryAcquire(properties.getAdmission().getRateWindowSeconds(), tenantPolicy.maxRequestsPerWindow())) {
            metrics.recordAdmission("rate_limited", request.streaming());
            return Mono.error(new AdmissionRejectedException("tenant_rate_limited", "Tenant rate limit exceeded."));
        }
        if (!request.streaming()) {
            metrics.recordAdmission("accepted", false);
            return Mono.just(AdmissionLease.noOp());
        }
        AtomicInteger tenantCounter = activeStreamsByTenant.computeIfAbsent(request.tenantId(), ignored -> new AtomicInteger());
        int global = activeStreams.incrementAndGet();
        if (global > properties.getAdmission().getMaxActiveStreams()) {
            activeStreams.decrementAndGet();
            metrics.recordAdmission("global_stream_rejected", true);
            return Mono.error(new AdmissionRejectedException("global_stream_limit_exceeded", "Too many active streams."));
        }
        int tenant = tenantCounter.incrementAndGet();
        if (tenant > tenantPolicy.maxActiveStreamsPerTenant()) {
            tenantCounter.decrementAndGet();
            activeStreams.decrementAndGet();
            metrics.recordAdmission("tenant_stream_rejected", true);
            return Mono.error(new AdmissionRejectedException("tenant_stream_limit_exceeded", "Tenant active stream quota exceeded."));
        }
        metrics.recordAdmission("accepted", true);
        return Mono.just(() -> Mono.fromRunnable(() -> {
            tenantCounter.decrementAndGet();
            activeStreams.decrementAndGet();
        }));
    }

    private static final class WindowCounter {
        private long windowStartMillis;
        private int count;

        synchronized boolean tryAcquire(int windowSeconds, int maxRequests) {
            long now = System.currentTimeMillis();
            long windowMillis = windowSeconds * 1000L;
            if (windowStartMillis == 0 || now - windowStartMillis >= windowMillis) {
                windowStartMillis = now;
                count = 0;
            }
            if (count >= maxRequests) {
                return false;
            }
            count++;
            return true;
        }
    }
}
