package ai.littleclaw.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "littleclaw")
public class LittleClawProperties {

    @NotBlank
    private String systemPrompt;

    private final Skills skills = new Skills();
    private final Stream stream = new Stream();
    private final Limits limits = new Limits();
    private final Provider provider = new Provider();
    private final Admission admission = new Admission();
    private final Rag rag = new Rag();
    private final Mcp mcp = new Mcp();
    private final Auth auth = new Auth();

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

    public Provider getProvider() {
        return provider;
    }

    public Admission getAdmission() {
        return admission;
    }

    public Rag getRag() {
        return rag;
    }

    public Mcp getMcp() {
        return mcp;
    }

    public Auth getAuth() {
        return auth;
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

        @Min(1)
        @Max(1024)
        private int downstreamPrefetch = 32;

        public int getTokenDelayMs() {
            return tokenDelayMs;
        }

        public void setTokenDelayMs(int tokenDelayMs) {
            this.tokenDelayMs = tokenDelayMs;
        }

        public int getDownstreamPrefetch() {
            return downstreamPrefetch;
        }

        public void setDownstreamPrefetch(int downstreamPrefetch) {
            this.downstreamPrefetch = downstreamPrefetch;
        }
    }

    public static class Limits {
        @Min(1)
        @Max(200)
        private int maxMessages = 40;

        @Min(256)
        @Max(200000)
        private int maxInputChars = 16000;

        @Min(32)
        @Max(50000)
        private int maxMessageChars = 4000;

        @Min(16)
        @Max(32000)
        private int defaultMaxTokens = 512;

        @Min(16)
        @Max(32000)
        private int maxMaxTokens = 4096;

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

        public int getMaxMessageChars() {
            return maxMessageChars;
        }

        public void setMaxMessageChars(int maxMessageChars) {
            this.maxMessageChars = maxMessageChars;
        }

        public int getDefaultMaxTokens() {
            return defaultMaxTokens;
        }

        public void setDefaultMaxTokens(int defaultMaxTokens) {
            this.defaultMaxTokens = defaultMaxTokens;
        }

        public int getMaxMaxTokens() {
            return maxMaxTokens;
        }

        public void setMaxMaxTokens(int maxMaxTokens) {
            this.maxMaxTokens = maxMaxTokens;
        }
    }

    public static class Provider {
        @NotBlank
        private String type = "stub";

        private final OpenAi openai = new OpenAi();

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public OpenAi getOpenai() {
            return openai;
        }
    }

    public static class OpenAi {
        @NotBlank
        private String baseUrl = "https://api.openai.com";

        @NotBlank
        private String chatCompletionsPath = "/v1/chat/completions";

        private String apiKey = "";

        @NotBlank
        private String model = "gpt-4o-mini";

        @Min(100)
        @Max(120000)
        private int connectTimeoutMs = 3000;

        @Min(100)
        @Max(600000)
        private int responseTimeoutMs = 90000;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getChatCompletionsPath() {
            return chatCompletionsPath;
        }

        public void setChatCompletionsPath(String chatCompletionsPath) {
            this.chatCompletionsPath = chatCompletionsPath;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public int getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

        public void setConnectTimeoutMs(int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
        }

        public int getResponseTimeoutMs() {
            return responseTimeoutMs;
        }

        public void setResponseTimeoutMs(int responseTimeoutMs) {
            this.responseTimeoutMs = responseTimeoutMs;
        }
    }

    public static class Admission {
        private boolean enabled = true;

        @Min(1)
        @Max(100000)
        private int maxActiveStreams = 500;

        @Min(1)
        @Max(100000)
        private int maxActiveStreamsPerTenant = 50;

        @Min(1)
        @Max(100000)
        private int maxRequestsPerWindow = 120;

        @Min(1)
        @Max(3600)
        private int rateWindowSeconds = 60;

        private boolean redisEnabled = false;

        @NotBlank
        private String redisPrefix = "littleclaw:admission";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxActiveStreams() {
            return maxActiveStreams;
        }

        public void setMaxActiveStreams(int maxActiveStreams) {
            this.maxActiveStreams = maxActiveStreams;
        }

        public int getMaxActiveStreamsPerTenant() {
            return maxActiveStreamsPerTenant;
        }

        public void setMaxActiveStreamsPerTenant(int maxActiveStreamsPerTenant) {
            this.maxActiveStreamsPerTenant = maxActiveStreamsPerTenant;
        }

        public int getMaxRequestsPerWindow() {
            return maxRequestsPerWindow;
        }

        public void setMaxRequestsPerWindow(int maxRequestsPerWindow) {
            this.maxRequestsPerWindow = maxRequestsPerWindow;
        }

        public int getRateWindowSeconds() {
            return rateWindowSeconds;
        }

        public void setRateWindowSeconds(int rateWindowSeconds) {
            this.rateWindowSeconds = rateWindowSeconds;
        }

        public boolean isRedisEnabled() {
            return redisEnabled;
        }

        public void setRedisEnabled(boolean redisEnabled) {
            this.redisEnabled = redisEnabled;
        }

        public String getRedisPrefix() {
            return redisPrefix;
        }

        public void setRedisPrefix(String redisPrefix) {
            this.redisPrefix = redisPrefix;
        }
    }

    public static class Rag {
        private boolean enabled = true;

        @Min(0)
        @Max(20)
        private int topK = 4;

        private final List<String> includePaths = new ArrayList<>(List.of("./docs", "./memory", "./skills"));

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getTopK() {
            return topK;
        }

        public void setTopK(int topK) {
            this.topK = topK;
        }

        public List<String> getIncludePaths() {
            return includePaths;
        }
    }

    public static class Auth {
        private boolean enabled = false;

        @NotBlank
        private String apiKeyHeader = "X-API-Key";

        private final List<String> staticApiKeys = new ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getApiKeyHeader() {
            return apiKeyHeader;
        }

        public void setApiKeyHeader(String apiKeyHeader) {
            this.apiKeyHeader = apiKeyHeader;
        }

        public List<String> getStaticApiKeys() {
            return staticApiKeys;
        }
    }

    public static class Mcp {
        private boolean enabled = true;

        private final List<Server> servers = new ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<Server> getServers() {
            return servers;
        }

        public static class Server {
            @NotBlank
            private String name = "default";

            @NotBlank
            private String transport = "stdio";

            private String command = "";
            private final List<String> args = new ArrayList<>();
            private final List<String> tools = new ArrayList<>();

            public String getName() {
                return name;
            }

            public void setName(String name) {
                this.name = name;
            }

            public String getTransport() {
                return transport;
            }

            public void setTransport(String transport) {
                this.transport = transport;
            }

            public String getCommand() {
                return command;
            }

            public void setCommand(String command) {
                this.command = command;
            }

            public List<String> getArgs() {
                return args;
            }

            public List<String> getTools() {
                return tools;
            }
        }
    }
}
