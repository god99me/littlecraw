package ai.littleclaw.api;

import ai.littleclaw.admission.AdmissionRejectedException;
import jakarta.validation.ValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleValidation(ValidationException exception) {
        return Map.of(
                "timestamp", Instant.now().toString(),
                "error", "validation_error",
                "message", exception.getMessage()
        );
    }

    @ExceptionHandler(AdmissionRejectedException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public Map<String, Object> handleAdmissionRejected(AdmissionRejectedException exception) {
        return Map.of(
                "timestamp", Instant.now().toString(),
                "error", "admission_rejected",
                "message", exception.getMessage()
        );
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, Object> handleGeneric(Exception exception) {
        return Map.of(
                "timestamp", Instant.now().toString(),
                "error", "internal_error",
                "message", exception.getMessage()
        );
    }
}
