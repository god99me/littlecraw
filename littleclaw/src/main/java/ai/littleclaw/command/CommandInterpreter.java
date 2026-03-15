package ai.littleclaw.command;

import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class CommandInterpreter {

    public CommandMatch match(String latestUserMessage) {
        if (latestUserMessage == null) {
            return new CommandMatch(CommandAction.NONE, false, "");
        }
        String normalized = latestUserMessage.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "/help", "help", "帮助" -> new CommandMatch(CommandAction.HELP, true, latestUserMessage);
            case "/stop", "stop", "停止", "打断" -> new CommandMatch(CommandAction.STOP, true, latestUserMessage);
            case "/regen", "/regenerate", "重新生成", "重来一版" -> new CommandMatch(CommandAction.REGENERATE, true, latestUserMessage);
            case "/ping", "ping" -> new CommandMatch(CommandAction.PING, true, latestUserMessage);
            default -> new CommandMatch(CommandAction.NONE, false, latestUserMessage);
        };
    }
}
