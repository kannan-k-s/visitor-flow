package ai.visitorflow.demo.web.security;

import ai.visitorflow.demo.data.exception.NotFoundException;
import ai.visitorflow.demo.data.tenant.TenantResolver;
import ai.visitorflow.demo.web.error.HttpErrorWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class TenantOAuthLoginFilter extends OncePerRequestFilter {
  private final TenantResolver tenantResolver;
  private final HttpErrorWriter errorWriter;

  @Override
  protected void doFilterInternal(
    HttpServletRequest request, HttpServletResponse response, FilterChain chain
  ) throws ServletException, IOException {
    String tenantName = segments(request)[0];
    try {
      tenantResolver.resolve(tenantName);
      request.setAttribute(TenantAuthorizationRequestResolver.TENANT_ATTRIBUTE, tenantName);
      chain.doFilter(request, response);
    } catch (NotFoundException exception) {
      errorWriter.write(response, HttpStatus.NOT_FOUND.value(), "not_found", exception.getMessage());
    }
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String[] path = segments(request);
    return path.length != 4 || !"v1".equals(path[1]) || !"auth".equals(path[2])
      || !"login".equals(path[3]);
  }

  private String[] segments(HttpServletRequest request) {
    return request.getRequestURI().replaceFirst("^/", "").split("/");
  }
}
