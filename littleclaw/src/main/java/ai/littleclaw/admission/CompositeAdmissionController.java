package ai.littleclaw.admission;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@Primary
public class CompositeAdmissionController implements AdmissionController {

    private final AdmissionController delegate;

    public CompositeAdmissionController(InMemoryAdmissionController fallback, ObjectProvider<RedisAdmissionController> redisController) {
        this.delegate = redisController.getIfAvailable(() -> fallback);
    }

    @Override
    public Mono<AdmissionLease> acquire(AdmissionRequest request) {
        return delegate.acquire(request);
    }
}
