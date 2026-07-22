package ai.visitorflow.demo.web.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class JwtServiceImplTest {
  private final JwtService service = new JwtServiceImpl(new SecurityProperties(
    Duration.ofMinutes(30), Duration.ofMinutes(5), "a-secure-test-key-containing-at-least-32-bytes",
    "session", false
  ));

  @Test
  void roundTripsTenantThroughSignedOAuthState() {
    String state = service.mintOAuthState("demo");

    assertThat(service.parseOAuthState(state)).isEqualTo("demo");
  }

  @Test
  void rejectsSessionTokenAsOAuthState() {
    String session = service.mint(7L, 11L, "admin@example.com");

    assertThatThrownBy(() -> service.parseOAuthState(session))
      .isInstanceOf(IllegalArgumentException.class);
  }
}
