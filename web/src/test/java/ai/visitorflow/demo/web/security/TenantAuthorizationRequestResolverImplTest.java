package ai.visitorflow.demo.web.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.web.util.UriComponentsBuilder;

class TenantAuthorizationRequestResolverImplTest {
  @Test
  void putsTenantOnlyInSignedState() {
    JwtService jwtService = new StubJwtService();
    TenantAuthorizationRequestResolver resolver = new TenantAuthorizationRequestResolverImpl(
      new InMemoryClientRegistrationRepository(List.of(google())), jwtService
    );
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/demo/v1/auth/login");
    request.setScheme("http");
    request.setServerName("localhost");
    request.setServerPort(8080);
    request.setAttribute(TenantAuthorizationRequestResolver.TENANT_ATTRIBUTE, "demo");

    OAuth2AuthorizationRequest authorizationRequest = resolver.resolve(request);

    assertThat(authorizationRequest.getState()).isEqualTo("signed-state-for-demo");
    var query = UriComponentsBuilder.fromUriString(authorizationRequest.getAuthorizationRequestUri())
      .build().getQueryParams();
    assertThat(query).doesNotContainKey("tenant");
    assertThat(query.getFirst("state")).isEqualTo("signed-state-for-demo");
  }

  private ClientRegistration google() {
    return ClientRegistration.withRegistrationId("google")
      .clientId("client-id")
      .clientSecret("client-secret")
      .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
      .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
      .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
      .scope("openid", "email")
      .authorizationUri("https://accounts.example.test/authorize")
      .tokenUri("https://accounts.example.test/token")
      .jwkSetUri("https://accounts.example.test/jwks")
      .userInfoUri("https://accounts.example.test/userinfo")
      .userNameAttributeName("sub")
      .clientName("Google")
      .build();
  }

  private static final class StubJwtService implements JwtService {
    @Override
    public String mint(Long userId, Long tenantId, String email) {
      throw new UnsupportedOperationException();
    }

    @Override
    public JwtPrincipal parse(String token) {
      throw new UnsupportedOperationException();
    }

    @Override
    public String mintOAuthState(String tenantName) {
      return "signed-state-for-" + tenantName;
    }

    @Override
    public String parseOAuthState(String token) {
      throw new UnsupportedOperationException();
    }
  }
}
