package ai.visitorflow.demo.web.security;

import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;

public interface TenantAuthorizationRequestResolver extends OAuth2AuthorizationRequestResolver {
  String TENANT_ATTRIBUTE = TenantAuthorizationRequestResolver.class.getName() + ".tenant";
}
