package ai.visitorflow.demo.data.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.cache")
public record ExperimentCacheProperties(Duration tenantTtl, Duration experimentTtl) {
}
