package ai.visitorflow.demo.visitor.assignment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.assignment")
public record AssignmentProperties(int maxExperiments) {
}
