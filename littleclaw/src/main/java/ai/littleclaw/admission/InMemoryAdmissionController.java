package ai.littleclaw.admission;

import ai.littleclaw.config.LittleClawProperties;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class InMemoryAdmissionController implements AdmissionController {

    private final LittleClawProperties properties;
    private final AtomicInteger activeStreams = new AtomicInteger();
    private final Map<String, AtomicInteger> activeStreamsByTenant = new ConcurrentHashMap<>();
    private final Map<String, WindowCounter> requestCounters = new ConcurrentHashMap<>();

    public InMemoryAdmissionController(LittleClawProperties properties) {
        this.properties = properties;
    }

    @Override
    public Mono<AdmissionLease> acquire(AdmissionRequest request) {
        if (!properties.getAdmission().isEnabled()) {
            return Mono.just(AdmissionLease.noOp());
        }
        WindowCounter counter = requestCounters.computeIfAbsent(request.tenantId(), ignored -> new WindowCounter());
        if (!counter.tryAcquire(properties.getAdmission().getRateWindowSeconds(), properties.getAdmission().getMaxRequestsPerWindow())) {
            return Mono.error(new AdmissionRejectedException("Tenant rate limit exceeded."));
        }
        if (!request.streaming()) {
            return Mono.just(AdmissionLease.noOp());
        }
        AtomicInteger tenantCounter = activeStreamsByTenant.computeIfAbsent(request.tenantId(), ignored -> new AtomicInteger());
        int global = activeStreams.incrementAndGet();
        if (global > properties.getAdmission().getMaxActiveStreams()) {
            activeStreams.decrementAndGet();
            return Mono.error(new AdmissionRejectedException("Too many active streams."));
        }
        int tenant = tenantCounter.incrementAndGet();
        if (tenant > properties.getAdmission().getMaxActiveStreamsPerTenant()) {
            tenantCounter.decrementAndGet();
            activeStreams.decrementAndGet();
            return Mono.error(new AdmissionRejectedException("Tenant active stream quota exceeded."));
        }
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
