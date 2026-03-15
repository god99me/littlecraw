package ai.littleclaw.admission;

public class AdmissionRejectedException extends RuntimeException {

    public AdmissionRejectedException(String message) {
        super(message);
    }
}
