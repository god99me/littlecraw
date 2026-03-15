package ai.littleclaw.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Component
public class ChatMetrics {

    private final MeterRegistry registry;
    private final AtomicInteger activeStreams = new AtomicInteger();

    public ChatMetrics(MeterRegistry registry) {
        this.registry = registry;
        Gauge.builder("littleclaw.stream.active", activeStreams, AtomicInteger::get)
                .description("Currently active SSE streams")
                .register(registry);
    }

    public Timer.Sample startRequest() {
        return Timer.start(registry);
    }

    public void recordCompletion(String action, String finishReason, String channel, boolean continuedConversation, Timer.Sample sample) {
        Counter.builder("littleclaw.chat.requests")
                .tag("action", action)
                .tag("finish_reason", finishReason)
                .tag("channel", sanitize(channel))
                .tag("continued_conversation", Boolean.toString(continuedConversation))
                .register(registry)
                .increment();
        sample.stop(Timer.builder("littleclaw.chat.request.duration")
                .tag("action", action)
                .tag("finish_reason", finishReason)
                .register(registry));
    }

    public void recordStreamStart(String action, String channel) {
        activeStreams.incrementAndGet();
        Counter.builder("littleclaw.chat.stream.starts")
                .tag("action", action)
                .tag("channel", sanitize(channel))
                .register(registry)
                .increment();
    }

    public void recordStreamEnd(String action, String finishReason, String channel, boolean continuedConversation) {
        activeStreams.updateAndGet(current -> Math.max(0, current - 1));
        Counter.builder("littleclaw.chat.stream.ends")
                .tag("action", action)
                .tag("finish_reason", finishReason)
                .tag("channel", sanitize(channel))
                .tag("continued_conversation", Boolean.toString(continuedConversation))
                .register(registry)
                .increment();
    }

    public void recordError(String action, String errorType, String channel) {
        Counter.builder("littleclaw.chat.errors")
                .tag("action", sanitize(action))
                .tag("error_type", sanitize(errorType))
                .tag("channel", sanitize(channel))
                .register(registry)
                .increment();
    }

    public void recordAuth(String outcome, String tenantId) {
        Counter.builder("littleclaw.auth.requests")
                .tag("outcome", sanitize(outcome))
                .tag("tenant", sanitize(tenantId))
                .register(registry)
                .increment();
    }

    public void recordAdmission(String outcome, boolean streaming) {
        Counter.builder("littleclaw.admission.requests")
                .tag("outcome", sanitize(outcome))
                .tag("streaming", Boolean.toString(streaming))
                .register(registry)
                .increment();
    }

    public void recordConversationStore(String operation, String outcome) {
        Counter.builder("littleclaw.session.store")
                .tag("operation", sanitize(operation))
                .tag("outcome", sanitize(outcome))
                .register(registry)
                .increment();
    }

    public void recordProvider(String provider, String outcome, boolean retryable) {
        Counter.builder("littleclaw.provider.calls")
                .tag("provider", sanitize(provider))
                .tag("outcome", sanitize(outcome))
                .tag("retryable", Boolean.toString(retryable))
                .register(registry)
                .increment();
    }

    private String sanitize(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
