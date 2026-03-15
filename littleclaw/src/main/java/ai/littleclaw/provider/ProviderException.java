package ai.littleclaw.provider;

public class ProviderException extends RuntimeException {

    private final String code;
    private final boolean retryable;
    private final int status;

    public ProviderException(String code, String message, boolean retryable, int status) {
        super(message);
        this.code = code;
        this.retryable = retryable;
        this.status = status;
    }

    public ProviderException(String code, String message, boolean retryable, int status, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.retryable = retryable;
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public int getStatus() {
        return status;
    }
}
