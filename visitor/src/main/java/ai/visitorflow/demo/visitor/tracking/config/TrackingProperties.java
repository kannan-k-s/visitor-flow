package ai.visitorflow.demo.visitor.tracking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.tracking")
public record TrackingProperties(int maxEntries) {
}
