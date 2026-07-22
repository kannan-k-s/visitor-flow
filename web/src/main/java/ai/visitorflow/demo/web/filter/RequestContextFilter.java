package ai.visitorflow.demo.web.filter;

import ai.visitorflow.demo.data.context.RequestContext;
import ai.visitorflow.demo.data.context.RequestContextHolder;
import ai.visitorflow.demo.data.exception.ForbiddenException;
import ai.visitorflow.demo.data.exception.NotFoundException;
import ai.visitorflow.demo.data.exception.ValidationException;
import ai.visitorflow.demo.data.identity.VisitorIdentityResolver;
import ai.visitorflow.demo.data.tenant.TenantResolver;
import ai.visitorflow.demo.web.error.HttpErrorWriter;
import ai.visitorflow.demo.web.security.JwtPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component("visitorRequestContextFilter")
@RequiredArgsConstructor
public class RequestContextFilter extends OncePerRequestFilter {
  private final TenantResolver tenantResolver;
  private final VisitorIdentityResolver visitorIdentityResolver;
  private final HttpErrorWriter errorWriter;

  @Override
  protected void doFilterInternal(
    HttpServletRequest request, HttpServletResponse response, FilterChain chain
  ) throws ServletException, IOException {
    String[] path = segments(request);
    try {
      Long tenantId = tenantResolver.resolve(path[0]);
      RequestContext context = resolveContext(request, tenantId, path[2]);
      try {
        RequestContextHolder.set(context);
        chain.doFilter(request, response);
      } finally {
        RequestContextHolder.clear();
      }
    } catch (NotFoundException exception) {
      errorWriter.write(response, HttpStatus.NOT_FOUND.value(), "not_found", exception.getMessage());
    } catch (ValidationException exception) {
      errorWriter.write(response, HttpStatus.BAD_REQUEST.value(), "invalid_request", exception.getMessage());
    } catch (ForbiddenException exception) {
      errorWriter.write(response, HttpStatus.FORBIDDEN.value(), "forbidden", exception.getMessage());
    }
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String[] path = segments(request);
    return path.length < 3 || !"v1".equals(path[1]);
  }

  private RequestContext resolveContext(HttpServletRequest request, Long tenantId, String operation) {
    String visitorId = header(request, "X-Visitor-Id");
    Long suppliedAnonId = anonId(request);
    Long anonVisitorId = null;
    Long userId = null;
    if ("assign".equals(operation)) {
      anonVisitorId = visitorIdentityResolver.resolveForAssign(tenantId, visitorId, suppliedAnonId);
    } else if ("track".equals(operation)) {
      anonVisitorId = visitorIdentityResolver.resolveForTrack(tenantId, visitorId, suppliedAnonId);
    } else {
      Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
      if (authentication != null && authentication.getPrincipal() instanceof JwtPrincipal principal) {
        if (!tenantId.equals(principal.tenantId())) {
          throw new ForbiddenException("Authenticated user does not belong to this tenant");
        }
        userId = principal.userId();
      }
    }
    return RequestContext.builder()
      .tenantId(tenantId)
      .userId(userId)
      .visitorId(visitorId)
      .anonVisitorId(anonVisitorId)
      .correlationId(correlationId(request))
      .build();
  }

  private Long anonId(HttpServletRequest request) {
    String value = header(request, "X-Anon-Id");
    if (value == null) {
      return null;
    }
    try {
      long anonId = Long.parseLong(value);
      if (anonId <= 0) {
        throw new NumberFormatException();
      }
      return anonId;
    } catch (NumberFormatException exception) {
      throw new ValidationException("X-Anon-Id must be a positive number");
    }
  }

  private String correlationId(HttpServletRequest request) {
    String supplied = header(request, "X-Correlation-Id");
    return supplied == null ? UUID.randomUUID().toString() : supplied;
  }

  private String header(HttpServletRequest request, String name) {
    String value = request.getHeader(name);
    return value == null || value.isBlank() ? null : value.trim();
  }

  private String[] segments(HttpServletRequest request) {
    return request.getRequestURI().replaceFirst("^/", "").split("/");
  }
}
