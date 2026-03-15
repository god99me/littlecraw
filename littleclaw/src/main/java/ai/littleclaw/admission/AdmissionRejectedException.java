package ai.littleclaw.admission;

public class AdmissionRejectedException extends RuntimeException {

    private final String code;

    public AdmissionRejectedException(String message) {
        this("admission_rejected", message);
    }

    public AdmissionRejectedException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
