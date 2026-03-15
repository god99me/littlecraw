package ai.littleclaw.api;

import ai.littleclaw.admission.AdmissionRejectedException;
import ai.littleclaw.observability.ChatMetrics;
import ai.littleclaw.provider.ProviderException;
import jakarta.validation.ValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebExchange;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private final ChatMetrics metrics;

    public GlobalExceptionHandler(ChatMetrics metrics) {
        this.metrics = metrics;
    }

    @ExceptionHandler(ValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleValidation(ValidationException exception, ServerWebExchange exchange) {
        metrics.recordError("request", "validation_error", requestContext(exchange).channel());
        return errorResponse("validation_error", exception.getMessage(), exchange, Map.of());
    }

    @ExceptionHandler(AdmissionRejectedException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public ApiErrorResponse handleAdmissionRejected(AdmissionRejectedException exception, ServerWebExchange exchange) {
        metrics.recordError("request", "admission_rejected", requestContext(exchange).channel());
        return errorResponse("admission_rejected", exception.getMessage(), exchange, Map.of());
    }

    @ExceptionHandler(ProviderException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public ApiErrorResponse handleProvider(ProviderException exception, ServerWebExchange exchange) {
        metrics.recordError("provider", exception.getCode(), requestContext(exchange).channel());
        return errorResponse("provider_error", exception.getMessage(), exchange, Map.of(
                "providerCode", exception.getCode(),
                "retryable", exception.isRetryable(),
                "providerStatus", exception.getStatus()
        ));
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiErrorResponse handleGeneric(Exception exception, ServerWebExchange exchange) {
        metrics.recordError("request", "internal_error", requestContext(exchange).channel());
        return errorResponse("internal_error", exception.getMessage(), exchange, Map.of());
    }

    private ApiErrorResponse errorResponse(String error, String message, ServerWebExchange exchange, Map<String, Object> details) {
        RequestContext requestContext = requestContext(exchange);
        return new ApiErrorResponse(
                Instant.now(),
                error,
                message,
                requestContext.requestId(),
                requestContext.tenantId(),
                details
        );
    }

    private RequestContext requestContext(ServerWebExchange exchange) {
        RequestContext requestContext = exchange.getAttribute(RequestContextFilter.ATTRIBUTE);
        return requestContext == null ? new RequestContext("anonymous", "unknown", "api") : requestContext;
    }
}
