package ai.visitorflow.demo.web.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ai.visitorflow.demo.data.tenant.TenantResolver;
import ai.visitorflow.demo.data.tenant.model.UserEntity;
import ai.visitorflow.demo.data.tenant.repository.UserRepository;
import ai.visitorflow.demo.web.error.HttpErrorWriter;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

class OAuthLoginSuccessHandlerImplTest {
  private static final String EMAIL = "admin@example.com";

  @Test
  void redirectsToAbsoluteHttpsWhenCookieSecure() throws Exception {
    MockHttpServletResponse response = handle(securityProperties(true), "app.example.com");
    assertThat(response.getRedirectedUrl()).isEqualTo("https://app.example.com/demo/experiments");
  }

  @Test
  void redirectsToRelativePathInDev() throws Exception {
    MockHttpServletResponse response = handle(securityProperties(false), "localhost");
    assertThat(response.getRedirectedUrl()).isEqualTo("/demo/experiments");
  }

  private MockHttpServletResponse handle(SecurityProperties properties, String serverName) throws Exception {
    TenantResolver tenantResolver = mock(TenantResolver.class);
    UserRepository userRepository = mock(UserRepository.class);
    JwtService jwtService = mock(JwtService.class);
    UserEntity user = mock(UserEntity.class);
    when(jwtService.parseOAuthState("state-token")).thenReturn("demo");
    when(tenantResolver.resolve("demo")).thenReturn(1L);
    when(userRepository.findByEmailAndTenantId(EMAIL, 1L)).thenReturn(Optional.of(user));
    when(user.getId()).thenReturn(10L);
    when(user.getEmail()).thenReturn(EMAIL);
    when(jwtService.mint(10L, 1L, EMAIL)).thenReturn("jwt");

    OAuthLoginSuccessHandlerImpl handler = new OAuthLoginSuccessHandlerImpl(
      tenantResolver, userRepository, jwtService, properties, mock(HttpErrorWriter.class)
    );
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login/oauth2/code/google");
    request.setServerName(serverName);
    request.setParameter("state", "state-token");
    MockHttpServletResponse response = new MockHttpServletResponse();

    handler.onAuthenticationSuccess(request, response, new TestingAuthenticationToken(oidcUser(), null));
    return response;
  }

  private DefaultOidcUser oidcUser() {
    OidcIdToken idToken = OidcIdToken.withTokenValue("token")
      .claim("sub", "123").claim("email", EMAIL).claim("email_verified", true)
      .build();
    return new DefaultOidcUser(List.of(), idToken);
  }

  private SecurityProperties securityProperties(boolean cookieSecure) {
    return new SecurityProperties(
      Duration.ofMinutes(30), Duration.ofMinutes(5), "secret", "session", cookieSecure
    );
  }
}
