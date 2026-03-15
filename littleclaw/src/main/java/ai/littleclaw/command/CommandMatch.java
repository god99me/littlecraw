package ai.littleclaw.command;

public record CommandMatch(
        CommandAction action,
        boolean matched,
        String rawText
) {
}
