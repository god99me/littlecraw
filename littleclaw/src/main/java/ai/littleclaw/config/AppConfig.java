package ai.littleclaw.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(LittleClawProperties.class)
public class AppConfig {
}
