package ai.visitorflow.demo.web.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
class JwtCookieAuthenticationFilter extends OncePerRequestFilter {
  private final JwtService jwtService;
  private final SecurityProperties properties;

  @Override
  protected void doFilterInternal(
    HttpServletRequest request, HttpServletResponse response, FilterChain chain
  ) throws ServletException, IOException {
    String token = cookie(request, properties.sessionCookieName());
    if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
      try {
        JwtPrincipal principal = jwtService.parse(token);
        SecurityContextHolder.getContext().setAuthentication(
          new UsernamePasswordAuthenticationToken(principal, token, List.of())
        );
      } catch (RuntimeException ignored) {
        // An invalid or expired session cookie is treated as unauthenticated.
      }
    }
    chain.doFilter(request, response);
  }

  private String cookie(HttpServletRequest request, String name) {
    if (request.getCookies() == null) {
      return null;
    }
    return Arrays.stream(request.getCookies())
      .filter(cookie -> name.equals(cookie.getName()))
      .map(Cookie::getValue)
      .findFirst()
      .orElse(null);
  }
}
