package ai.visitorflow.demo.web.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

@Component
class TenantAuthorizationRequestResolverImpl implements TenantAuthorizationRequestResolver {
  private final DefaultOAuth2AuthorizationRequestResolver delegate;
  private final JwtService jwtService;

  TenantAuthorizationRequestResolverImpl(ClientRegistrationRepository registrations, JwtService jwtService) {
    delegate = new DefaultOAuth2AuthorizationRequestResolver(registrations, "/oauth2/authorization");
    this.jwtService = jwtService;
  }

  @Override
  public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
    String tenantName = tenantName(request);
    if (tenantName != null) {
      return withTenant(delegate.resolve(request, "google"), tenantName);
    }
    return delegate.resolve(request);
  }

  @Override
  public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
    return withTenant(delegate.resolve(request, clientRegistrationId), tenantName(request));
  }

  private OAuth2AuthorizationRequest withTenant(OAuth2AuthorizationRequest request, String tenantName) {
    if (request == null || tenantName == null || tenantName.isBlank()) {
      return request;
    }
    return OAuth2AuthorizationRequest.from(request)
      .state(jwtService.mintOAuthState(tenantName))
      .authorizationRequestUri((String) null)
      .build();
  }

  private String tenantName(HttpServletRequest request) {
    Object tenantName = request.getAttribute(TENANT_ATTRIBUTE);
    return tenantName instanceof String value ? value : null;
  }
}
