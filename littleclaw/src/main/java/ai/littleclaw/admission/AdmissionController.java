package ai.littleclaw.admission;

import reactor.core.publisher.Mono;

public interface AdmissionController {

    Mono<AdmissionLease> acquire(AdmissionRequest request);
}
