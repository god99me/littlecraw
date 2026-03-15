package ai.littleclaw.admission;

import reactor.core.publisher.Mono;

@FunctionalInterface
public interface AdmissionLease {

    Mono<Void> release();

    static AdmissionLease noOp() {
        return () -> Mono.empty();
    }
}
