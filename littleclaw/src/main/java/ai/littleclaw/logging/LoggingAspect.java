package ai.littleclaw.logging;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Aspect
@Component
public class LoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(LoggingAspect.class);

    @Around("@annotation(timedOperation)")
    public Object logTiming(ProceedingJoinPoint joinPoint, TimedOperation timedOperation) throws Throwable {
        long start = System.currentTimeMillis();
        Object result = joinPoint.proceed();
        if (result instanceof Mono<?> mono) {
            return mono.doOnSuccess(ignored -> log.info("op={} durationMs={} type=mono", timedOperation.value(), System.currentTimeMillis() - start))
                    .doOnError(error -> log.warn("op={} durationMs={} type=mono error={}", timedOperation.value(), System.currentTimeMillis() - start, error.toString()));
        }
        if (result instanceof Flux<?> flux) {
            return flux.doOnComplete(() -> log.info("op={} durationMs={} type=flux", timedOperation.value(), System.currentTimeMillis() - start))
                    .doOnError(error -> log.warn("op={} durationMs={} type=flux error={}", timedOperation.value(), System.currentTimeMillis() - start, error.toString()));
        }
        log.info("op={} durationMs={} type=sync", timedOperation.value(), System.currentTimeMillis() - start);
        return result;
    }
}
