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
  private final SecurityProperties properties;

  TenantAuthorizationRequestResolverImpl(
    ClientRegistrationRepository registrations, JwtService jwtService, SecurityProperties properties
  ) {
    delegate = new DefaultOAuth2AuthorizationRequestResolver(registrations, "/oauth2/authorization");
    this.jwtService = jwtService;
    this.properties = properties;
  }

  @Override
  public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
    String tenantName = tenantName(request);
    if (tenantName != null) {
      return customize(delegate.resolve(request, "google"), tenantName);
    }
    return customize(delegate.resolve(request), null);
  }

  @Override
  public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
    return customize(delegate.resolve(request, clientRegistrationId), tenantName(request));
  }

  private OAuth2AuthorizationRequest customize(OAuth2AuthorizationRequest request, String tenantName) {
    if (request == null) {
      return null;
    }
    boolean hasTenant = tenantName != null && !tenantName.isBlank();
    // Reuse cookieSecure as the "served over https" signal: the proxy terminates TLS, so the
    // servlet request looks like http — force the redirect_uri to https where cookies are secure.
    boolean forceHttps = properties.cookieSecure() && request.getRedirectUri() != null
      && request.getRedirectUri().startsWith("http://");
    if (!hasTenant && !forceHttps) {
      return request;
    }
    OAuth2AuthorizationRequest.Builder builder = OAuth2AuthorizationRequest.from(request)
      .authorizationRequestUri((String) null);
    if (forceHttps) {
      builder.redirectUri("https://" + request.getRedirectUri().substring("http://".length()));
    }
    if (hasTenant) {
      builder.state(jwtService.mintOAuthState(tenantName));
    }
    return builder.build();
  }

  private String tenantName(HttpServletRequest request) {
    Object tenantName = request.getAttribute(TENANT_ATTRIBUTE);
    return tenantName instanceof String value ? value : null;
  }
}
