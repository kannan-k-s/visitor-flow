package ai.visitorflow.demo.web.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.security")
public record SecurityProperties(
  Duration jwtTtl, Duration oauthStateTtl, String jwtSigningSecret, String sessionCookieName,
  boolean cookieSecure
) {
}
