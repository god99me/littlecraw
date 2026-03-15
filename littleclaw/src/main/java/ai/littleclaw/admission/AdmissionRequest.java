package ai.littleclaw.admission;

public record AdmissionRequest(
        String tenantId,
        boolean streaming,
        int requestedMaxTokens
) {
}
