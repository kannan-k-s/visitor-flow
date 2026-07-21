package ai.visitorflow.demo.web.security;

import ai.visitorflow.demo.data.tenant.TenantResolver;
import ai.visitorflow.demo.data.tenant.model.UserEntity;
import ai.visitorflow.demo.data.tenant.repository.UserRepository;
import ai.visitorflow.demo.web.error.HttpErrorWriter;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class OAuthLoginSuccessHandlerImpl implements OAuthLoginSuccessHandler {
  private final TenantResolver tenantResolver;
  private final UserRepository userRepository;
  private final JwtService jwtService;
  private final SecurityProperties properties;
  private final HttpErrorWriter errorWriter;

  @Override
  public void onAuthenticationSuccess(
    HttpServletRequest request, HttpServletResponse response, Authentication authentication
  ) throws IOException, ServletException {
    String tenantName = tenant(request);
    if (tenantName == null || !(authentication.getPrincipal() instanceof OidcUser oidcUser)
      || oidcUser.getEmail() == null || !Boolean.TRUE.equals(oidcUser.getEmailVerified())) {
      errorWriter.write(response, HttpStatus.FORBIDDEN.value(), "forbidden", "OAuth identity is not allowed");
      return;
    }
    Long tenantId = tenantResolver.resolve(tenantName);
    UserEntity user = userRepository.findByEmailAndTenantId(oidcUser.getEmail(), tenantId).orElse(null);
    if (user == null) {
      errorWriter.write(response, HttpStatus.FORBIDDEN.value(), "forbidden", "User has not been invited");
      return;
    }
    addCookie(response, properties.sessionCookieName(), jwtService.mint(
      user.getId(), tenantId, user.getEmail()
    ), properties.jwtTtl());
    if (request.getSession(false) != null) {
      request.getSession(false).invalidate();
    }
    response.sendRedirect("/swagger-ui.html");
  }

  private void addCookie(HttpServletResponse response, String name, String value, Duration maxAge) {
    ResponseCookie cookie = ResponseCookie.from(name, value)
      .httpOnly(true)
      .secure(properties.cookieSecure())
      .sameSite("Lax")
      .path("/")
      .maxAge(maxAge)
      .build();
    response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
  }

  private String tenant(HttpServletRequest request) {
    String state = request.getParameter("state");
    if (state == null || state.isBlank()) {
      return null;
    }
    try {
      return jwtService.parseOAuthState(state);
    } catch (RuntimeException exception) {
      return null;
    }
  }
}
