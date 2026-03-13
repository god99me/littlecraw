package ai.littleclaw.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "littleclaw")
public class LittleClawProperties {

    @NotBlank
    private String systemPrompt;

    private final Skills skills = new Skills();
    private final Stream stream = new Stream();
    private final Limits limits = new Limits();

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public Skills getSkills() {
        return skills;
    }

    public Stream getStream() {
        return stream;
    }

    public Limits getLimits() {
        return limits;
    }

    public static class Skills {
        @NotBlank
        private String path = "./skills";

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }
    }

    public static class Stream {
        @Min(0)
        @Max(1000)
        private int tokenDelayMs = 15;

        public int getTokenDelayMs() {
            return tokenDelayMs;
        }

        public void setTokenDelayMs(int tokenDelayMs) {
            this.tokenDelayMs = tokenDelayMs;
        }
    }

    public static class Limits {
        @Min(1)
        @Max(200)
        private int maxMessages = 40;

        @Min(256)
        @Max(200000)
        private int maxInputChars = 16000;

        public int getMaxMessages() {
            return maxMessages;
        }

        public void setMaxMessages(int maxMessages) {
            this.maxMessages = maxMessages;
        }

        public int getMaxInputChars() {
            return maxInputChars;
        }

        public void setMaxInputChars(int maxInputChars) {
            this.maxInputChars = maxInputChars;
        }
    }
}
